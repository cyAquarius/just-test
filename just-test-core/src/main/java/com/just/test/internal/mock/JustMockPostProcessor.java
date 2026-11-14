package com.just.test.internal.mock;

import com.just.test.annotation.ThreadScopedMock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.type.MethodMetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将 {@code @JustMock} 声明的类型 + {@code @ThreadScopedMock} 标记的 @Bean 方法
 * 统一替换为 thread-scoped + ScopedProxy 的 mock bean，并把 {@code @JustSpy}
 * 声明的已有 Bean 换成委托给真实实例的 thread-scoped mock。
 *
 * <p>对每个 mock 类型：找到现有 bean → 移除 → 注册 thread-scoped mock → 包装 ScopedProxy。
 * 原 bean 名称指向 proxy，每次方法调用委托给当前线程的 mock 实例。</p>
 *
 * <p>{@code @JustSpy} 不在这里实例化真实 Bean，只把原定义挪到隐藏名
 * （非 autowire candidate）。公开名仍是 thread-scoped ScopedProxy；每个线程的 mock
 * 用 {@code delegatesTo} 把未 stub 的调用转给 refresh 创建的那个容器 Bean
 * （若该 Bean 是 AOP 代理，则委托给代理本身）。线程作用域 mock 实现
 * {@link org.springframework.aop.framework.AopInfrastructureBean}，创建后不再被 advising / auto-proxy 包一层；
 * 隐藏的真实 Bean 不加这个标记，{@code @Repository} / {@code @Transactional}
 * 仍作用在它上面。</p>
 *
 * <p>来源：
 * <ol>
 *   <li>{@code mockTypes} —— 测试类字段 {@code @JustMock} 扫描所得（per-test 差异化 mock）</li>
 *   <li>{@code @ThreadScopedMock} —— Configuration 类 {@code @Bean} 方法标记（全局外部依赖 mock）</li>
 *   <li>opt-in {@code @JustTestProject(autoMockFeignClients = true)} 发现的 {@code @FeignClient}
 *       接口。{@code @JustMock} 若解析到这些已经是线程级 mock 的 Bean，只绑定字段，不再次注册。
 *       {@code @JustSpy} / {@code @ThreadScopedMock} 优先</li>
 *   <li>{@code @JustTestProject} 上的真实 Bean 拦截：隐藏真实 Bean，公开名换成按线程切换默认答案的
 *       Mockito mock。本测试类声明了 {@code @JustMock} 时未 stub 走 Mockito 默认值，否则
 *       {@code delegatesTo} 真实 Bean。这些声明不重复注册定义。接口与实现解析到同一个
 *       Bean 名时只注册一次；无法安全合并则在启动期失败。</li>
 *   <li>{@code @JustSpy} —— 只包裹已有 Bean，找不到则失败，不会 create-if-absent</li>
 * </ol>
 * </p>
 * <p>Bean 名称解析、intercept 合并安装，以及 mock / spy / Feign 注册，分别委托同包的
 * {@link BeanNameResolver}、{@link InterceptInstaller}、{@link MockRegistrar}。
 * 这些类型不是公开 API。</p>
 */
class JustMockPostProcessor implements BeanFactoryPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(JustMockPostProcessor.class);
    static final String SCOPED_TARGET_PREFIX = "scopedTarget.";
    static final String SPY_TARGET_PREFIX = "justSpy.target.";
    static final String INTERCEPT_TARGET_PREFIX = "justMock.intercept.target.";

    private final Set<JustMockDefinition> mockDefinitions;
    private final Set<JustMockDefinition> spyDefinitions;
    private final JustMockInterceptPlan interceptPlan;
    private final BeanNameResolver names;
    private final MockRegistrar registrar;
    private final InterceptInstaller intercepts;

    JustMockPostProcessor(Set<JustMockDefinition> mockDefinitions) {
        this(mockDefinitions, Collections.<JustMockDefinition>emptySet());
    }

    JustMockPostProcessor(Set<JustMockDefinition> mockDefinitions, Set<JustMockDefinition> spyDefinitions) {
        this(mockDefinitions, spyDefinitions, JustMockInterceptPlan.none());
    }

    JustMockPostProcessor(Set<JustMockDefinition> mockDefinitions,
                          Set<JustMockDefinition> spyDefinitions,
                          JustMockInterceptPlan interceptPlan) {
        this.mockDefinitions = mockDefinitions;
        this.spyDefinitions = spyDefinitions;
        this.interceptPlan = interceptPlan == null ? JustMockInterceptPlan.none() : interceptPlan;
        this.names = new BeanNameResolver();
        this.registrar = new MockRegistrar(this.names);
        this.intercepts = new InterceptInstaller(this.interceptPlan, this.names, this.registrar);
    }

    static String spyTargetBeanName(String publicName) {
        return SPY_TARGET_PREFIX + publicName;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        if (!(beanFactory instanceof BeanDefinitionRegistry)) {
            log.warn("[JustMock] BeanFactory is not a BeanDefinitionRegistry, skipping");
            return;
        }
        BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;

        // 同类型的 @JustSpy 必须先失败。已经是线程级 mock 的 @JustMock 只绑定，不重复注册。
        Set<JustMockDefinition> threadScoped = collectThreadScopedMockDefinitions(beanFactory);
        Map<String, JustMockDefinition> threadScopedByName = new LinkedHashMap<String, JustMockDefinition>();
        for (JustMockDefinition definition : threadScoped) {
            threadScopedByName.put(definition.getExplicitBeanName(), definition);
        }
        Set<JustMockDefinition> spyConflictInputs = new LinkedHashSet<JustMockDefinition>(mockDefinitions);
        spyConflictInputs.addAll(threadScoped);
        rejectSpyMockTypeConflicts(spyDefinitions, spyConflictInputs);

        Map<JustMockDefinition, String> spyNames = new LinkedHashMap<JustMockDefinition, String>();
        Map<String, JustMockDefinition> spiesByBeanName = new LinkedHashMap<String, JustMockDefinition>();
        for (JustMockDefinition spy : spyDefinitions) {
            String beanName = names.resolveBeanName(beanFactory, registry, spy);
            JustMockDefinition previous = spiesByBeanName.put(beanName, spy);
            if (previous != null) {
                throw duplicateSpyTarget(beanName, previous, spy);
            }
            spyNames.put(spy, beanName);
        }

        Map<Class<?>, String> feignDiscovered = registrar.discoverFeignTypes(beanFactory, registry);
        Set<Class<?>> feignBlocked = mockedTypes(threadScoped);
        feignBlocked.addAll(mockedTypes(spyDefinitions));

        Map<JustMockDefinition, String> bindings = new LinkedHashMap<JustMockDefinition, String>();
        List<JustMockDefinition> pending = new ArrayList<JustMockDefinition>();
        for (JustMockDefinition fieldMock : mockDefinitions) {
            String pendingFeign = registrar.pendingFeignBind(fieldMock, feignDiscovered, feignBlocked);
            if (pendingFeign != null) {
                bindings.put(fieldMock, pendingFeign);
                log.info("[JustMock] Binding {} to existing thread-scoped mock '{}'",
                        fieldMock.describe(), pendingFeign);
                continue;
            }
            String resolved = names.resolveBeanName(beanFactory, registry, fieldMock);
            JustMockDefinition spy = spiesByBeanName.get(resolved);
            if (spy != null) {
                throw JustMockBeanOrigins.sameBeanSpyMockConflict(resolved, spy, fieldMock);
            }
            String bindName = registrar.bindTarget(fieldMock, resolved, threadScopedByName, feignDiscovered, feignBlocked);
            if (bindName != null) {
                bindings.put(fieldMock, bindName);
                log.info("[JustMock] Binding {} to existing thread-scoped mock '{}'",
                        fieldMock.describe(), bindName);
            } else {
                pending.add(fieldMock);
            }
        }

        Set<String> registeredBeanNames = new LinkedHashSet<String>();
        Set<String> interceptBeanNames = new LinkedHashSet<String>();
        Map<Class<?>, String> installedIntercepts = intercepts.installIntercepts(
                beanFactory, registry, threadScoped, spiesByBeanName, feignDiscovered,
                bindings, registeredBeanNames, interceptBeanNames, feignBlocked);

        List<JustMockDefinition> replacements = new ArrayList<JustMockDefinition>();
        for (JustMockDefinition fieldMock : pending) {
            String installed = InterceptInstaller.installedIntercept(fieldMock.getType(), installedIntercepts);
            if (installed != null) {
                bindings.put(fieldMock, installed);
                log.info("[JustMock] Binding {} to thread intercept '{}'", fieldMock.describe(), installed);
                continue;
            }
            if (interceptPlan.isProject()) {
                throw interceptPlan.notInstalled(fieldMock);
            }
            replacements.add(fieldMock);
            feignBlocked.add(fieldMock.getType());
        }

        registrar.registerMockDefinitions(beanFactory, registry, threadScoped, spiesByBeanName,
                bindings, registeredBeanNames);
        registrar.registerMockDefinitions(beanFactory, registry,
                registrar.feignDefinitions(feignDiscovered, feignBlocked), spiesByBeanName,
                bindings, registeredBeanNames);
        registrar.registerMockDefinitions(beanFactory, registry, replacements, spiesByBeanName,
                bindings, registeredBeanNames);
        for (Map.Entry<JustMockDefinition, String> spy : spyNames.entrySet()) {
            String beanName = registrar.registerThreadScopedSpy(beanFactory, registry, spy.getKey(), spy.getValue());
            registeredBeanNames.add(beanName);
            bindings.put(spy.getKey(), beanName);
            registrar.markMockSource(registry, beanName, spy.getKey());
        }

        rejectMockAutoConfigurationConflicts(beanFactory, bindings);
        registerBindingsBean(registry, bindings);
        registerInterceptRegistry(registry, interceptBeanNames);
        // 注册 Registry bean，供 JustTestExtension 在 case 开始时预热所有 thread-scoped mock
        registerRegistryBean(registry, registeredBeanNames);
    }

    private void registerInterceptRegistry(BeanDefinitionRegistry registry, Set<String> interceptBeanNames) {
        final Set<String> snapshot = new LinkedHashSet<String>(interceptBeanNames);
        BeanDefinition definition = BeanDefinitionBuilder
                .genericBeanDefinition(JustMockInterceptRegistry.class,
                        () -> new JustMockInterceptRegistry(snapshot))
                .getBeanDefinition();
        registry.registerBeanDefinition("justMockInterceptRegistry", definition);
    }

    private void registerBindingsBean(BeanDefinitionRegistry registry,
                                      Map<JustMockDefinition, String> bindings) {
        BeanDefinition definition = BeanDefinitionBuilder
                .genericBeanDefinition(JustMockBindings.class,
                        () -> new JustMockBindings(bindings, interceptPlan))
                .getBeanDefinition();
        registry.registerBeanDefinition("justMockBindings", definition);
    }

    private void registerRegistryBean(BeanDefinitionRegistry registry, Set<String> beanNames) {
        Set<String> snapshot = new LinkedHashSet<>(beanNames);
        BeanDefinition registryDef = BeanDefinitionBuilder
                .genericBeanDefinition(ThreadScopedMockRegistry.class,
                        () -> new ThreadScopedMockRegistry(snapshot))
                .getBeanDefinition();
        registry.registerBeanDefinition("justMockThreadScopedMockRegistry", registryDef);
        log.info("[JustMock] Registered ThreadScopedMockRegistry with {} beans", snapshot.size());
    }

    /**
     * 扫描所有 BeanDefinition，找出工厂方法带 {@link ThreadScopedMock} 注解的类型。
     *
     * <p>Spring 处理 {@code @Configuration} 时，每个 {@code @Bean} 方法会产生
     * {@code AnnotatedBeanDefinition}，通过 {@code getFactoryMethodMetadata()} 可读取方法级注解。</p>
     */
    private Set<JustMockDefinition> collectThreadScopedMockDefinitions(ConfigurableListableBeanFactory beanFactory) {
        Set<JustMockDefinition> definitions = new LinkedHashSet<>();
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition bd = beanFactory.getBeanDefinition(beanName);
            if (!(bd instanceof AnnotatedBeanDefinition)) {
                continue;
            }
            MethodMetadata factoryMethod = ((AnnotatedBeanDefinition) bd).getFactoryMethodMetadata();
            if (factoryMethod == null) {
                continue;
            }
            if (!factoryMethod.isAnnotated(ThreadScopedMock.class.getName())) {
                continue;
            }
            // 读 Bean 定义元数据。getType(name) 允许初始化 FactoryBean，这里不用它。
            Class<?> type = names.threadScopedMockType(beanFactory, beanName, bd);
            if (type == null) {
                log.warn("[JustMock] @ThreadScopedMock bean '{}' has null type, skipping", beanName);
                continue;
            }
            definitions.add(JustMockDefinition.forBeanName(type, beanName, "@ThreadScopedMock"));
            log.debug("[JustMock] Discovered @ThreadScopedMock bean '{}' of type {}", beanName, type.getSimpleName());
        }
        return definitions;
    }

    private static Set<Class<?>> mockedTypes(Set<JustMockDefinition> definitions) {
        Set<Class<?>> types = new LinkedHashSet<>();
        for (JustMockDefinition definition : definitions) {
            types.add(definition.getType());
        }
        return types;
    }

    private static IllegalStateException duplicateSpyTarget(String beanName,
                                                           JustMockDefinition first,
                                                           JustMockDefinition second) {
        return new IllegalStateException("[JustSpy] " + first.describe() + " and " + second.describe()
                + " both resolved to bean '" + beanName + "'. One bean can only be wrapped once.");
    }

    private void rejectSpyMockTypeConflicts(Set<JustMockDefinition> spies, Set<JustMockDefinition> mocks) {
        Map<Class<?>, List<JustMockDefinition>> mocksByType =
                groupByType(mocks);
        Map<Class<?>, List<JustMockDefinition>> spiesByType =
                groupByType(spies);
        for (Map.Entry<Class<?>, List<JustMockDefinition>> entry : spiesByType.entrySet()) {
            List<JustMockDefinition> mockSide = mocksByType.get(entry.getKey());
            if (mockSide == null || mockSide.isEmpty()) {
                continue;
            }
            throw JustMockBeanOrigins.spyAndMockConflict(entry.getKey(), entry.getValue(), mockSide);
        }
    }

    private static Map<Class<?>, List<JustMockDefinition>> groupByType(
            Set<JustMockDefinition> definitions) {
        Map<Class<?>, List<JustMockDefinition>> grouped =
                new LinkedHashMap<Class<?>, List<JustMockDefinition>>();
        for (JustMockDefinition definition : definitions) {
            List<JustMockDefinition> group = grouped.get(definition.getType());
            if (group == null) {
                group = new ArrayList<JustMockDefinition>();
                grouped.put(definition.getType(), group);
            }
            group.add(definition);
        }
        return grouped;
    }

    /**
     * {@code @JustMock} / {@code @ThreadScopedMock} 只替换自己选中的那个 Bean。
     * 同类型若还留下第三方 {@code *AutoConfiguration} 候选，按类型注入会变成
     * {@code NoUniqueBeanDefinitionException}。这里在 Context refresh 期间 fail-fast，
     * 不自动 exclude，也不改 {@code @Resource} 语义。
     */
    private void rejectMockAutoConfigurationConflicts(ConfigurableListableBeanFactory beanFactory,
                                                      Map<JustMockDefinition, String> bindings) {
        Map<String, String> mockSources = new LinkedHashMap<String, String>();
        for (Map.Entry<JustMockDefinition, String> entry : bindings.entrySet()) {
            mockSources.put(entry.getValue(), entry.getKey().sourceLabel());
        }
        Set<Class<?>> seenTypes = new LinkedHashSet<Class<?>>();
        for (JustMockDefinition definition : bindings.keySet()) {
            Class<?> type = definition.getType();
            if (!seenTypes.add(type)) {
                continue;
            }
            List<JustMockBeanOrigins.Candidate> candidates = JustMockBeanOrigins.describeCandidates(
                    beanFactory, names.collectLogicalCandidates(beanFactory, type, false), mockSources);
            if (JustMockBeanOrigins.hasMockAndAutoConfiguration(candidates)) {
                throw JustMockBeanOrigins.conflict(type, candidates);
            }
        }
    }

}

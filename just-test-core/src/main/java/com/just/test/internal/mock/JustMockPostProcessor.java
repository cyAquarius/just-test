package com.just.test.internal.mock;

import com.just.test.annotation.ThreadScopedMock;
import com.just.test.internal.project.JustTestProjectFeignAutoMock;
import org.mockito.AdditionalAnswers;
import org.mockito.MockSettings;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.core.ResolvableType;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
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
 * {@link AopInfrastructureBean}，创建后不再被 advising / auto-proxy 包一层；
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
 */
class JustMockPostProcessor implements BeanFactoryPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(JustMockPostProcessor.class);
    private static final String SCOPED_TARGET_PREFIX = "scopedTarget.";
    static final String SPY_TARGET_PREFIX = "justSpy.target.";
    static final String INTERCEPT_TARGET_PREFIX = "justMock.intercept.target.";

    private final Set<JustMockDefinition> mockDefinitions;
    private final Set<JustMockDefinition> spyDefinitions;
    private final JustMockInterceptPlan interceptPlan;

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
            String beanName = resolveBeanName(beanFactory, registry, spy);
            JustMockDefinition previous = spiesByBeanName.put(beanName, spy);
            if (previous != null) {
                throw duplicateSpyTarget(beanName, previous, spy);
            }
            spyNames.put(spy, beanName);
        }

        Map<Class<?>, String> feignDiscovered = discoverFeignTypes(beanFactory, registry);
        Set<Class<?>> feignBlocked = mockedTypes(threadScoped);
        feignBlocked.addAll(mockedTypes(spyDefinitions));

        Map<JustMockDefinition, String> bindings = new LinkedHashMap<JustMockDefinition, String>();
        List<JustMockDefinition> pending = new ArrayList<JustMockDefinition>();
        for (JustMockDefinition fieldMock : mockDefinitions) {
            String pendingFeign = pendingFeignBind(fieldMock, feignDiscovered, feignBlocked);
            if (pendingFeign != null) {
                bindings.put(fieldMock, pendingFeign);
                log.info("[JustMock] Binding {} to existing thread-scoped mock '{}'",
                        fieldMock.describe(), pendingFeign);
                continue;
            }
            String resolved = resolveBeanName(beanFactory, registry, fieldMock);
            JustMockDefinition spy = spiesByBeanName.get(resolved);
            if (spy != null) {
                throw JustMockBeanOrigins.sameBeanSpyMockConflict(resolved, spy, fieldMock);
            }
            String bindName = bindTarget(fieldMock, resolved, threadScopedByName, feignDiscovered, feignBlocked);
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
        Map<Class<?>, String> installedIntercepts = installIntercepts(
                beanFactory, registry, threadScoped, spiesByBeanName, feignDiscovered,
                bindings, registeredBeanNames, interceptBeanNames, feignBlocked);

        List<JustMockDefinition> replacements = new ArrayList<JustMockDefinition>();
        for (JustMockDefinition fieldMock : pending) {
            String installed = installedIntercept(fieldMock.getType(), installedIntercepts);
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

        registerMockDefinitions(beanFactory, registry, threadScoped, spiesByBeanName,
                bindings, registeredBeanNames);
        registerMockDefinitions(beanFactory, registry,
                feignDefinitions(feignDiscovered, feignBlocked), spiesByBeanName,
                bindings, registeredBeanNames);
        registerMockDefinitions(beanFactory, registry, replacements, spiesByBeanName,
                bindings, registeredBeanNames);
        for (java.util.Map.Entry<JustMockDefinition, String> spy : spyNames.entrySet()) {
            String beanName = registerThreadScopedSpy(beanFactory, registry, spy.getKey(), spy.getValue());
            registeredBeanNames.add(beanName);
            bindings.put(spy.getKey(), beanName);
            markMockSource(registry, beanName, spy.getKey());
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
                                      java.util.Map<JustMockDefinition, String> bindings) {
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
            Class<?> type = beanFactory.getType(beanName);
            if (type == null) {
                log.warn("[JustMock] @ThreadScopedMock bean '{}' has null type, skipping", beanName);
                continue;
            }
            definitions.add(JustMockDefinition.forBeanName(type, beanName, "@ThreadScopedMock"));
            log.debug("[JustMock] Discovered @ThreadScopedMock bean '{}' of type {}", beanName, type.getSimpleName());
        }
        return definitions;
    }

    private void registerMockDefinitions(ConfigurableListableBeanFactory beanFactory,
                                         BeanDefinitionRegistry registry,
                                         Iterable<JustMockDefinition> definitions,
                                         Map<String, JustMockDefinition> spiesByBeanName,
                                         Map<JustMockDefinition, String> bindings,
                                         Set<String> registeredBeanNames) {
        for (JustMockDefinition definition : definitions) {
            String beanName = registerThreadScopedMock(beanFactory, registry, definition, spiesByBeanName);
            if (beanName != null) {
                registeredBeanNames.add(beanName);
                bindings.put(definition, beanName);
                markMockSource(registry, beanName, definition);
            }
        }
    }

    /**
     * Feign 客户端还没注册成 Bean 时，auto-mock 会按简单类名创建它。
     * 这种 {@code @JustMock} 直接绑定，避免先走“找不到 Bean、准备新建”的告警。
     */
    private String pendingFeignBind(JustMockDefinition fieldMock,
                                    Map<Class<?>, String> feignDiscovered,
                                    Set<Class<?>> feignBlocked) {
        if (feignDiscovered == null || feignDiscovered.isEmpty()
                || feignBlocked.contains(fieldMock.getType())
                || !feignDiscovered.containsKey(fieldMock.getType())
                || fieldMock.hasQualifierAnnotations()
                || !fieldMock.getExplicitBeanName().isEmpty()) {
            return null;
        }
        String existing = feignDiscovered.get(fieldMock.getType());
        if (existing != null && !existing.isEmpty()) {
            return null;
        }
        return JustMockDefinition.decapitalize(fieldMock.getType().getSimpleName());
    }

    /**
     * 字段 {@code @JustMock} 解析到的 Bean 若已由 {@code @ThreadScopedMock} 或 Feign auto-mock 覆盖，
     * 返回那个 Bean 名；否则返回 null，调用方按原逻辑替换定义。
     */
    private String bindTarget(JustMockDefinition fieldMock,
                              String resolved,
                              Map<String, JustMockDefinition> threadScopedByName,
                              Map<Class<?>, String> feignDiscovered,
                              Set<Class<?>> feignBlocked) {
        if (resolved != null && threadScopedByName.containsKey(resolved)) {
            return resolved;
        }
        if (feignDiscovered == null || feignDiscovered.isEmpty()
                || feignBlocked.contains(fieldMock.getType())
                || !feignDiscovered.containsKey(fieldMock.getType())) {
            return null;
        }
        String existing = feignDiscovered.get(fieldMock.getType());
        String feignName = existing == null || existing.isEmpty()
                ? JustMockDefinition.decapitalize(fieldMock.getType().getSimpleName())
                : existing;
        return feignName.equals(resolved) ? feignName : null;
    }

    private Map<Class<?>, String> discoverFeignTypes(ConfigurableListableBeanFactory beanFactory,
                                                     BeanDefinitionRegistry registry) {
        if (!registry.containsBeanDefinition(JustTestProjectFeignAutoMock.BEAN_NAME)
                && !beanFactory.containsSingleton(JustTestProjectFeignAutoMock.BEAN_NAME)) {
            return Collections.emptyMap();
        }
        JustTestProjectFeignAutoMock settings = beanFactory.getBean(
                JustTestProjectFeignAutoMock.BEAN_NAME, JustTestProjectFeignAutoMock.class);
        return settings.discover(beanFactory);
    }

    private Set<JustMockDefinition> feignDefinitions(Map<Class<?>, String> discovered,
                                                     Set<Class<?>> blocked) {
        Set<JustMockDefinition> definitions = new LinkedHashSet<JustMockDefinition>();
        if (discovered == null || discovered.isEmpty()) {
            return definitions;
        }
        for (Map.Entry<Class<?>, String> entry : discovered.entrySet()) {
            Class<?> type = entry.getKey();
            if (blocked.contains(type)) {
                log.debug("[JustMock] Skipping auto @FeignClient {} because an explicit mock or @JustSpy already exists",
                        type.getName());
                continue;
            }
            definitions.add(JustMockDefinition.forAutoMock(
                    type, entry.getValue(), JustTestProjectFeignAutoMock.SOURCE));
            log.info("[JustMock] Auto-mocking @FeignClient {}", type.getName());
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

    private String registerThreadScopedMock(ConfigurableListableBeanFactory beanFactory,
                                            BeanDefinitionRegistry registry,
                                            JustMockDefinition definition,
                                            java.util.Map<String, JustMockDefinition> spiesByBeanName) {
        String targetBeanName = resolveBeanName(beanFactory, registry, definition);
        JustMockDefinition spy = spiesByBeanName.get(targetBeanName);
        if (spy != null) {
            throw JustMockBeanOrigins.sameBeanSpyMockConflict(targetBeanName, spy, definition);
        }
        Class<?> mockType = resolveMockType(beanFactory, registry, targetBeanName, definition);
        FinalMethodMockGuard.check(mockType);
        BeanDefinition originalDefinition = null;
        BeanDefinition originalScopedTarget = null;
        String[] aliases = null;
        if (registry.containsBeanDefinition(targetBeanName)) {
            originalDefinition = registry.getBeanDefinition(targetBeanName);
            aliases = beanFactory.getAliases(targetBeanName).clone();
            registry.removeBeanDefinition(targetBeanName);
            String scopedTargetName = ScopedProxyUtils.getTargetBeanName(targetBeanName);
            if (registry.containsBeanDefinition(scopedTargetName)) {
                originalScopedTarget = registry.getBeanDefinition(scopedTargetName);
                registry.removeBeanDefinition(scopedTargetName);
            }
            log.debug("[JustMock] Removed existing bean definition: {}", targetBeanName);
        }

        // 注册 thread-scoped mock bean
        AbstractBeanDefinition mockDef = createMockBeanDefinition(mockType);
        copyAutowireMetadata(mockDef, originalDefinition, originalScopedTarget);
        BeanDefinitionHolder holder = new BeanDefinitionHolder(mockDef, targetBeanName);
        BeanDefinitionHolder proxy = ScopedProxyUtils.createScopedProxy(holder, registry, true);
        if (proxy.getBeanDefinition() instanceof AbstractBeanDefinition) {
            copyAutowireMetadata((AbstractBeanDefinition) proxy.getBeanDefinition(),
                    originalDefinition, originalScopedTarget);
        }
        registry.registerBeanDefinition(targetBeanName, proxy.getBeanDefinition());
        restoreBeanAliases(beanFactory, registry, targetBeanName, aliases);

        log.info("[JustMock] Registered thread-scoped mock for {} as '{}'",
                mockType.getSimpleName(), targetBeanName);
        return targetBeanName;
    }

    /**
     * 替换或隐藏后只按规范名重新注册。{@code removeBeanDefinition} 可能同时丢掉别名，
     * 所以先快照，新定义落定后再 {@code registerAlias}。名字若已是 Bean 定义、
     * 单例，或指向其他 Bean 的别名，则跳过并警告，不覆盖已有注册。
     */
    private void restoreBeanAliases(ConfigurableListableBeanFactory beanFactory,
                                    BeanDefinitionRegistry registry,
                                    String beanName,
                                    String[] aliases) {
        if (aliases == null || aliases.length == 0) {
            return;
        }
        for (String alias : aliases) {
            if (alias == null || alias.length() == 0 || alias.equals(beanName)) {
                continue;
            }
            if (aliasConflicts(beanFactory, registry, beanName, alias)) {
                log.warn("[JustMock] Skipping alias '{}' for bean '{}': it conflicts with an existing "
                        + "bean name or alias. The existing registration is left unchanged.",
                        alias, beanName);
                // 同名 Bean 定义已经存在时，仍指向本 Bean 的旧别名会盖住它。摘掉这条别名，不改那份定义。
                if (registry.containsBeanDefinition(alias) && aliasesBean(registry, beanName, alias)) {
                    registry.removeAlias(alias);
                }
                continue;
            }
            registry.registerAlias(beanName, alias);
        }
    }

    private static boolean aliasesBean(BeanDefinitionRegistry registry, String beanName, String alias) {
        for (String existing : registry.getAliases(beanName)) {
            if (alias.equals(existing)) {
                return true;
            }
        }
        return false;
    }

    private static boolean aliasConflicts(ConfigurableListableBeanFactory beanFactory,
                                          BeanDefinitionRegistry registry,
                                          String beanName,
                                          String alias) {
        if (registry.containsBeanDefinition(alias) || beanFactory.containsSingleton(alias)) {
            return true;
        }
        return registry.isAlias(alias) && !aliasesBean(registry, beanName, alias);
    }

    private Class<?> resolveMockType(ConfigurableListableBeanFactory beanFactory,
                                     BeanDefinitionRegistry registry,
                                     String beanName,
                                     JustMockDefinition definition) {
        if (!registry.containsBeanDefinition(beanName) && !beanFactory.containsSingleton(beanName)) {
            return definition.getType();
        }
        Class<?> targetType = safeBeanType(beanFactory, beanName);
        if (targetType == null || !definition.getType().isAssignableFrom(targetType)) {
            return definition.getType();
        }
        return targetType;
    }

    private static boolean isInternalBeanName(String candidate) {
        return candidate.startsWith(SCOPED_TARGET_PREFIX)
                || candidate.startsWith(SPY_TARGET_PREFIX)
                || candidate.startsWith(INTERCEPT_TARGET_PREFIX);
    }

    private Map<Class<?>, String> installIntercepts(ConfigurableListableBeanFactory beanFactory,
                                                    BeanDefinitionRegistry registry,
                                                    Set<JustMockDefinition> threadScoped,
                                                    Map<String, JustMockDefinition> spiesByBeanName,
                                                    Map<Class<?>, String> feignDiscovered,
                                                    Map<JustMockDefinition, String> bindings,
                                                    Set<String> registeredBeanNames,
                                                    Set<String> interceptBeanNames,
                                                    Set<Class<?>> feignBlocked) {
        Map<Class<?>, String> installed = new LinkedHashMap<Class<?>, String>();
        if (!interceptPlan.isProject()) {
            return installed;
        }
        // 先按原始 Bean 定义解析全部声明类型，再按 Bean 名合并注册。
        // 实现类先于接口安装时，不能把已经包过的公开代理再当成真实 Bean 包第二层。
        List<InterceptResolution> resolutions = new ArrayList<InterceptResolution>();
        Set<Class<?>> seen = new LinkedHashSet<Class<?>>();
        for (Class<?> type : interceptPlan.explicitTypes()) {
            if (!seen.add(type)) {
                continue;
            }
            InterceptResolution resolution = resolveIntercept(
                    beanFactory, registry, type, true, threadScoped, spiesByBeanName, feignDiscovered);
            if (resolution != null) {
                resolutions.add(resolution);
            }
        }
        for (Class<?> type : interceptPlan.scannedTypes()) {
            if (interceptPlan.isExplicit(type) || !seen.add(type)) {
                continue;
            }
            InterceptResolution resolution = resolveIntercept(
                    beanFactory, registry, type, false, threadScoped, spiesByBeanName, feignDiscovered);
            if (resolution != null) {
                resolutions.add(resolution);
            }
        }
        for (InterceptResolution resolution : resolutions) {
            if (resolution.ambiguous()) {
                throw ambiguousIntercept(resolution, resolutions);
            }
        }
        Map<String, List<InterceptResolution>> byBean =
                new LinkedHashMap<String, List<InterceptResolution>>();
        for (InterceptResolution resolution : resolutions) {
            List<InterceptResolution> group = byBean.get(resolution.beanName);
            if (group == null) {
                group = new ArrayList<InterceptResolution>();
                byBean.put(resolution.beanName, group);
            }
            group.add(resolution);
        }
        for (Map.Entry<String, List<InterceptResolution>> entry : byBean.entrySet()) {
            registerMergedIntercept(beanFactory, registry, entry.getKey(), entry.getValue(),
                    bindings, registeredBeanNames, interceptBeanNames, feignBlocked, installed);
        }
        return installed;
    }

    private InterceptResolution resolveIntercept(ConfigurableListableBeanFactory beanFactory,
                                                 BeanDefinitionRegistry registry,
                                                 Class<?> type,
                                                 boolean explicit,
                                                 Set<JustMockDefinition> threadScoped,
                                                 Map<String, JustMockDefinition> spiesByBeanName,
                                                 Map<Class<?>, String> feignDiscovered) {
        if (feignDiscovered != null && feignDiscovered.containsKey(type)) {
            if (explicit) {
                throw alreadyThreadScoped(type);
            }
            log.debug("[JustMock] {} is a Feign auto-mock; @JustMock binds and is not intercepted",
                    type.getName());
            return null;
        }
        Set<String> threadScopedNames = threadScopedByNames(threadScoped);
        LinkedHashSet<String> candidates = collectLogicalCandidates(beanFactory, type, false);
        boolean onlyThreadScoped = !candidates.isEmpty() && threadScopedNames.containsAll(candidates);
        candidates.removeAll(threadScopedNames);
        if (candidates.isEmpty() && onlyThreadScoped) {
            if (explicit) {
                throw alreadyThreadScoped(type);
            }
            log.debug("[JustMock] {} is already a thread-scoped mock; @JustMock binds and is not intercepted",
                    type.getName());
            return null;
        }
        if (candidates.size() > 1) {
            return InterceptResolution.ambiguous(type, candidates);
        }
        if (candidates.isEmpty()) {
            if (explicit) {
                throw new IllegalStateException("[JustMock] @JustTestProject(interceptMocks) lists "
                        + type.getName() + " on " + interceptPlan.startupClass().getName()
                        + " but no real bean of that type exists. "
                        + "Intercept wraps the existing unique bean and does not create one.");
            }
            log.debug("[JustMock] No real bean of {} to intercept; a later @JustMock of this type fails fast",
                    type.getName());
            return null;
        }
        String publicName = candidates.iterator().next();
        if (spiesByBeanName.containsKey(publicName)) {
            log.debug("[JustMock] {} is a @JustSpy target in this context and is not intercepted", publicName);
            return null;
        }
        Class<?> mockType = resolveMockType(beanFactory, registry, publicName,
                JustMockDefinition.forBeanName(type, publicName, JustMockInterceptActivation.SOURCE));
        return InterceptResolution.unique(type, publicName, mockType);
    }

    private void registerMergedIntercept(ConfigurableListableBeanFactory beanFactory,
                                         BeanDefinitionRegistry registry,
                                         String publicName,
                                         List<InterceptResolution> group,
                                         Map<JustMockDefinition, String> bindings,
                                         Set<String> registeredBeanNames,
                                         Set<String> interceptBeanNames,
                                         Set<Class<?>> feignBlocked,
                                         Map<Class<?>, String> installed) {
        Class<?> mockType = mockTypeForGroup(beanFactory, publicName, group);
        FinalMethodMockGuard.check(mockType);
        assertMockable(mockType);
        registerThreadScopedIntercept(beanFactory, registry, mockType, publicName);
        registeredBeanNames.add(publicName);
        interceptBeanNames.add(publicName);
        LinkedHashSet<Class<?>> boundTypes = new LinkedHashSet<Class<?>>();
        boundTypes.add(mockType);
        for (InterceptResolution resolution : group) {
            boundTypes.add(resolution.type);
            rememberIntercept(installed, resolution.type, publicName);
            feignBlocked.add(resolution.type);
        }
        rememberIntercept(installed, mockType, publicName);
        feignBlocked.add(mockType);
        for (Class<?> boundType : boundTypes) {
            bindings.put(JustMockDefinition.forBeanName(
                    boundType, publicName, JustMockInterceptActivation.SOURCE), publicName);
        }
        markSourceAttribute(registry, publicName, JustMockInterceptActivation.SOURCE);
        markSourceAttribute(registry, ScopedProxyUtils.getTargetBeanName(publicName),
                JustMockInterceptActivation.SOURCE);
        log.info("[JustMock] Registered thread intercept for {} as '{}'", mockType.getName(), publicName);
        if (group.size() > 1) {
            log.info("[JustMock] Merged declaration types {} onto thread intercept '{}'",
                    declarationTypes(group), publicName);
        }
    }

    /**
     * 同一 Bean 只选一个 mock 类型。单个声明保持原先的 {@code resolveMockType} 结果。
     * 多个声明必须都能接受这个 mock（字段类型是 mock 类型的超类型或相同类型）。
     */
    private Class<?> mockTypeForGroup(ConfigurableListableBeanFactory beanFactory,
                                      String beanName,
                                      List<InterceptResolution> group) {
        if (group.size() == 1) {
            return group.get(0).mockType;
        }
        Class<?> beanType = safeBeanType(beanFactory, beanName);
        Class<?> mockType = beanType;
        if (mockType == null) {
            mockType = group.get(0).mockType;
            for (InterceptResolution resolution : group) {
                if (mockType.isAssignableFrom(resolution.mockType)) {
                    mockType = resolution.mockType;
                }
            }
        }
        for (InterceptResolution resolution : group) {
            if (mockType == null || !resolution.type.isAssignableFrom(mockType)) {
                throw unsafeMerge(beanName, mockType, group);
            }
        }
        if (beanType != null && !mockType.isAssignableFrom(beanType)) {
            throw unsafeMerge(beanName, mockType, group);
        }
        return mockType;
    }

    private IllegalStateException unsafeMerge(String beanName,
                                              Class<?> mockType,
                                              List<InterceptResolution> group) {
        String mockTypeName = mockType == null ? "(unknown)" : mockType.getName();
        return new IllegalStateException("[JustMock] Cannot merge thread intercept onto bean '"
                + beanName + "'. Incompatible mock type " + mockTypeName
                + ". Declaration types: " + declarationTypes(group)
                + ". Declared by: " + declarers(group)
                + ". JustTest will not register a second intercept.");
    }

    private IllegalStateException ambiguousIntercept(InterceptResolution ambiguous,
                                                     List<InterceptResolution> resolutions) {
        StringBuilder conflicts = new StringBuilder();
        for (InterceptResolution resolution : resolutions) {
            if (resolution.ambiguous() || resolution.beanName == null
                    || !ambiguous.candidates.contains(resolution.beanName)) {
                continue;
            }
            if (conflicts.length() > 0) {
                conflicts.append("; ");
            }
            conflicts.append(resolution.type.getName())
                    .append(" -> '")
                    .append(resolution.beanName)
                    .append("' declared by ")
                    .append(interceptPlan.declaredBy(resolution.type));
        }
        StringBuilder message = new StringBuilder();
        message.append("[JustMock] Cannot merge thread intercept for declaration type ")
                .append(ambiguous.type.getName())
                .append(" declared by ")
                .append(interceptPlan.declaredBy(ambiguous.type))
                .append(". Multiple beans found: ")
                .append(ambiguous.candidates)
                .append('.');
        if (conflicts.length() > 0) {
            message.append(" Conflicting declaration types: ").append(conflicts).append('.');
        }
        message.append(" JustTest will not choose between them or register a second intercept.");
        return new IllegalStateException(message.toString());
    }

    private static String declarationTypes(List<InterceptResolution> group) {
        StringBuilder builder = new StringBuilder();
        for (InterceptResolution resolution : group) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(resolution.type.getName());
        }
        return builder.toString();
    }

    private String declarers(List<InterceptResolution> group) {
        StringBuilder builder = new StringBuilder();
        for (InterceptResolution resolution : group) {
            if (builder.length() > 0) {
                builder.append("; ");
            }
            builder.append(resolution.type.getName())
                    .append(" by ")
                    .append(interceptPlan.declaredBy(resolution.type));
        }
        return builder.toString();
    }

    private static final class InterceptResolution {
        private final Class<?> type;
        private final String beanName;
        private final Class<?> mockType;
        private final LinkedHashSet<String> candidates;

        private InterceptResolution(Class<?> type,
                                    String beanName,
                                    Class<?> mockType,
                                    LinkedHashSet<String> candidates) {
            this.type = type;
            this.beanName = beanName;
            this.mockType = mockType;
            this.candidates = candidates;
        }

        private static InterceptResolution unique(Class<?> type, String beanName, Class<?> mockType) {
            return new InterceptResolution(type, beanName, mockType, null);
        }

        private static InterceptResolution ambiguous(Class<?> type, LinkedHashSet<String> candidates) {
            return new InterceptResolution(type, null, null, candidates);
        }

        private boolean ambiguous() {
            return candidates != null;
        }
    }

    private static void rememberIntercept(Map<Class<?>, String> installed, Class<?> type, String beanName) {
        String previous = installed.put(type, beanName);
        if (previous != null && !previous.equals(beanName)) {
            throw new IllegalStateException("[JustMock] Intercept type " + type.getName()
                    + " resolved to both '" + previous + "' and '" + beanName
                    + "'. JustTest does not expose a second bean of the same type.");
        }
    }

    private static String installedIntercept(Class<?> type, Map<Class<?>, String> installed) {
        String exact = installed.get(type);
        if (exact != null) {
            return exact;
        }
        String matched = null;
        for (Map.Entry<Class<?>, String> entry : installed.entrySet()) {
            if (!type.isAssignableFrom(entry.getKey()) && !entry.getKey().isAssignableFrom(type)) {
                continue;
            }
            if (matched != null && !matched.equals(entry.getValue())) {
                throw new IllegalStateException("[JustMock] Multiple thread intercepts match "
                        + type.getName() + ". Candidates include '" + matched + "' and '"
                        + entry.getValue() + "'. JustTest does not choose between them "
                        + "or auto-exclude auto-configurations.");
            }
            matched = entry.getValue();
        }
        return matched;
    }

    private static Set<String> threadScopedByNames(Set<JustMockDefinition> threadScoped) {
        Set<String> names = new LinkedHashSet<String>();
        for (JustMockDefinition definition : threadScoped) {
            if (definition.getExplicitBeanName() != null && !definition.getExplicitBeanName().isEmpty()) {
                names.add(definition.getExplicitBeanName());
            }
        }
        return names;
    }

    private static IllegalStateException alreadyThreadScoped(Class<?> type) {
        return new IllegalStateException("[JustMock] " + type.getName()
                + " is already a thread-scoped mock (@ThreadScopedMock or Feign auto-mock). "
                + "Do not list it in @JustTestProject(interceptMocks); @JustMock binds to that mock "
                + "and does not wrap it again.");
    }

    private static void assertMockable(Class<?> type) {
        try {
            Mockito.mock(type, threadScopedMockSettings());
        } catch (RuntimeException ex) {
            throw new IllegalStateException("[JustMock] Cannot intercept " + type.getName()
                    + " at context start. Mockito cannot proxy this type "
                    + "(final class, or the current mock-maker cannot mock it). "
                    + ex.getMessage(), ex);
        }
    }

    private void registerThreadScopedIntercept(ConfigurableListableBeanFactory beanFactory,
                                               BeanDefinitionRegistry registry,
                                               Class<?> mockType,
                                               String publicName) {
        if (!registry.containsBeanDefinition(publicName)) {
            throw new IllegalStateException("[JustMock] No bean definition named '" + publicName
                    + "' to intercept for " + mockType.getName()
                    + ". Intercept wraps an existing bean and does not create one.");
        }
        BeanDefinition original = registry.getBeanDefinition(publicName);
        String scopedTargetName = ScopedProxyUtils.getTargetBeanName(publicName);
        BeanDefinition originalScopedTarget = registry.containsBeanDefinition(scopedTargetName)
                ? registry.getBeanDefinition(scopedTargetName) : null;
        String hiddenName = INTERCEPT_TARGET_PREFIX + publicName;

        AbstractBeanDefinition mockDef = createInterceptBeanDefinition(
                mockType, beanFactory, hiddenName, publicName);
        copyAutowireMetadata(mockDef, original, originalScopedTarget);

        if (originalScopedTarget != null) {
            registry.removeBeanDefinition(scopedTargetName);
            String hiddenScopedName = ScopedProxyUtils.getTargetBeanName(hiddenName);
            retargetScopedProxy(original, hiddenScopedName);
            hideRealTarget(originalScopedTarget);
            registry.registerBeanDefinition(hiddenScopedName, originalScopedTarget);
        }

        BeanDefinitionHolder holder = new BeanDefinitionHolder(mockDef, publicName);
        BeanDefinitionHolder proxy = ScopedProxyUtils.createScopedProxy(holder, registry, true);
        if (proxy.getBeanDefinition() instanceof AbstractBeanDefinition) {
            copyAutowireMetadata((AbstractBeanDefinition) proxy.getBeanDefinition(),
                    original, originalScopedTarget);
        }

        String[] aliases = beanFactory.getAliases(publicName).clone();
        registry.removeBeanDefinition(publicName);
        hideRealTarget(original);
        registry.registerBeanDefinition(hiddenName, original);
        registry.registerBeanDefinition(publicName, proxy.getBeanDefinition());
        restoreBeanAliases(beanFactory, registry, publicName, aliases);
    }

    private <T> AbstractBeanDefinition createInterceptBeanDefinition(final Class<T> mockType,
                                                                     final ConfigurableListableBeanFactory beanFactory,
                                                                     final String hiddenName,
                                                                     final String publicName) {
        return BeanDefinitionBuilder
                .genericBeanDefinition(mockType, () -> createInterceptMock(
                        mockType, beanFactory, hiddenName, publicName))
                .setScope("thread")
                .getBeanDefinition();
    }

    private <T> T createInterceptMock(Class<T> mockType,
                                      ConfigurableListableBeanFactory beanFactory,
                                      String hiddenName,
                                      String publicName) {
        Object realBean = beanFactory.getBean(hiddenName);
        if (!mockType.isInstance(realBean)) {
            throw new IllegalStateException("[JustMock] Bean '" + hiddenName + "' is not a "
                    + mockType.getName() + "; cannot delegate unstubbed calls to it.");
        }
        return Mockito.mock(mockType, threadScopedMockSettings().defaultAnswer(
                new JustMockInterceptActivation.AnswerSwitch(
                        publicName, AdditionalAnswers.delegatesTo(realBean))));
    }

    private String resolveBeanName(ConfigurableListableBeanFactory beanFactory,
                                   BeanDefinitionRegistry registry,
                                   JustMockDefinition definition) {
        java.util.LinkedHashSet<String> logicalCandidates =
                collectLogicalCandidates(beanFactory, definition.getType(), false);
        if (!definition.getExplicitBeanName().isEmpty()) {
            String existingBeanName = findExistingBeanDefinition(
                    registry, beanFactory, definition.getExplicitBeanName());
            if (existingBeanName != null
                    && isTypeCompatible(beanFactory, existingBeanName, definition)) {
                return existingBeanName;
            }
            if (definition.isCreateIfAbsent()) {
                return resolveCreateIfAbsent(beanFactory, definition, logicalCandidates);
            }
            if (logicalCandidates.isEmpty()) {
                logicalCandidates.addAll(
                        collectLogicalCandidates(beanFactory, definition.getType(), true));
            }
            return requireCandidate(beanFactory, definition, logicalCandidates,
                    definition.getExplicitBeanName());
        }

        DependencyDescriptor descriptor = definition.toDependencyDescriptor();
        if (logicalCandidates.isEmpty()) {
            String fallback = resolveNamedFallback(beanFactory, registry, definition, descriptor);
            if (fallback != null) {
                return fallback;
            }
            // FactoryBean object types are not always available without eager initialization.
            // The name lookup above keeps this fallback independent of eager-init side effects.
            logicalCandidates.addAll(
                    collectLogicalCandidates(beanFactory, definition.getType(), true));
        }

        return resolveFromTypeCandidates(beanFactory, registry, definition,
                descriptor, logicalCandidates);
    }

    private java.util.LinkedHashSet<String> collectLogicalCandidates(
            ConfigurableListableBeanFactory beanFactory, Class<?> type, boolean allowEagerInit) {
        String[] candidates = beanFactory.getBeanNamesForType(type, true, allowEagerInit);
        java.util.LinkedHashSet<String> logicalCandidates = new java.util.LinkedHashSet<>();
        for (String candidate : candidates) {
            if (!isInternalBeanName(candidate)) {
                logicalCandidates.add(candidate);
            }
        }
        if (!allowEagerInit) {
            // Read product metadata without constructing FactoryBeans (notably Feign clients).
            // Always union candidates: a visible bean must not hide a second mapper (#65).
            for (String name : beanFactory.getBeanDefinitionNames()) {
                if (!isInternalBeanName(name)) {
                    Class<?> productType = factoryProductType(beanFactory,
                            beanFactory.getMergedBeanDefinition(name));
                    if (productType != null && type.isAssignableFrom(productType)) {
                        logicalCandidates.add(name);
                    }
                }
            }
        }
        return logicalCandidates;
    }

    private static Class<?> factoryProductType(ConfigurableListableBeanFactory factory,
                                             BeanDefinition definition) {
        if (definition.isAbstract()) {
            return null;
        }
        Class<?> objectType = metadataClass(factory, definition.getAttribute("factoryBeanObjectType"));
        if (objectType != null) {
            return objectType;
        }
        Class<?> beanClass = metadataClass(factory, definition.getBeanClassName());
        // Avoid a core dependency on mybatis-spring; also accept MapperFactoryBean subclasses.
        while (beanClass != null) {
            if ("org.mybatis.spring.mapper.MapperFactoryBean".equals(beanClass.getName())) {
                PropertyValue property =
                        definition.getPropertyValues().getPropertyValue("mapperInterface");
                if (property != null) {
                    return metadataClass(factory, property.getValue());
                }
                ConstructorArgumentValues arguments =
                        definition.getConstructorArgumentValues();
                ConstructorArgumentValues.ValueHolder indexed =
                        arguments.getIndexedArgumentValue(0, null);
                if (indexed != null) {
                    return metadataClass(factory, indexed.getValue());
                }
                if (arguments.getGenericArgumentValues().size() == 1) {
                    return metadataClass(factory, arguments.getGenericArgumentValues().get(0).getValue());
                }
                return null;
            }
            beanClass = beanClass.getSuperclass();
        }
        return null;
    }

    private static Class<?> metadataClass(ConfigurableListableBeanFactory factory, Object value) {
        if (value instanceof Class<?>) {
            return (Class<?>) value;
        }
        if (value instanceof ResolvableType) {
            return ((ResolvableType) value).resolve();
        }
        if (value instanceof TypedStringValue) {
            value = ((TypedStringValue) value).getValue();
        }
        if (value instanceof String) {
            try {
                return Class.forName((String) value, false, factory.getBeanClassLoader());
            } catch (ClassNotFoundException | LinkageError ex) {
                return null;
            }
        }
        return null;
    }

    private String resolveCreateIfAbsent(ConfigurableListableBeanFactory beanFactory,
                                         JustMockDefinition definition,
                                         Set<String> logicalCandidates) {
        java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>(logicalCandidates);
        if (candidates.isEmpty()) {
            // 不 eager-init：避免实例化真实 FeignClientFactoryBean（可能缺 LoadBalancer）
            candidates.addAll(collectLogicalCandidates(beanFactory, definition.getType(), false));
        }
        if (candidates.size() == 1) {
            return candidates.iterator().next();
        }
        if (candidates.isEmpty()) {
            return definition.getExplicitBeanName();
        }
        for (String candidate : candidates) {
            if (matchesName(beanFactory, candidate, definition.getExplicitBeanName())) {
                return candidate;
            }
        }
        throw ambiguous(beanFactory, definition, candidates);
    }

    private String resolveFromTypeCandidates(ConfigurableListableBeanFactory beanFactory,
                                             BeanDefinitionRegistry registry,
                                             JustMockDefinition definition,
                                             DependencyDescriptor descriptor,
                                             Set<String> logicalCandidates) {
        if (!definition.getExplicitBeanName().isEmpty()) {
            return requireCandidate(beanFactory, definition, logicalCandidates,
                    definition.getExplicitBeanName());
        }

        java.util.LinkedHashSet<String> injectableCandidates = new java.util.LinkedHashSet<>();
        for (String candidate : logicalCandidates) {
            if (beanFactory.isAutowireCandidate(candidate, descriptor)) {
                injectableCandidates.add(candidate);
            }
        }

        if (injectableCandidates.isEmpty()) {
            if (definition.hasQualifierAnnotations()) {
                throw new IllegalStateException(label(definition) + " No autowire candidate matches the qualifier for "
                        + definition.describe() + ". Type candidates: " + logicalCandidates);
            }
            String fallback = resolveNamedFallback(beanFactory, registry, definition, descriptor);
            if (fallback != null) {
                return fallback;
            }
            if (definition.isSpy()) {
                throw missingSpyBean(definition, logicalCandidates);
            }
            String generated = generateAvailableBeanName(
                    definition.getType(), beanFactory, registry);
            log.warn("[JustMock] No injectable bean found for {}, registering '{}'", definition.describe(), generated);
            return generated;
        }
        String primary = null;
        for (String candidate : injectableCandidates) {
            if (beanFactory.containsBeanDefinition(candidate)
                    && beanFactory.getBeanDefinition(candidate).isPrimary()) {
                if (primary != null) {
                    throw ambiguous(beanFactory, definition, injectableCandidates);
                }
                primary = candidate;
            }
        }
        if (primary != null) return primary;
        for (String candidate : injectableCandidates) {
            if (matchesName(beanFactory, candidate, definition.getFieldName())) {
                return candidate;
            }
        }
        if (injectableCandidates.size() == 1) return injectableCandidates.iterator().next();
        throw ambiguous(beanFactory, definition, injectableCandidates);
    }

    private String resolveNamedFallback(ConfigurableListableBeanFactory beanFactory,
                                        BeanDefinitionRegistry registry,
                                        JustMockDefinition definition,
                                        DependencyDescriptor descriptor) {
        String fieldName = definition.getFieldName();
        String fieldBeanName = findExistingBeanDefinition(registry, beanFactory, fieldName);
        if (fieldBeanName != null
                && beanFactory.isAutowireCandidate(fieldBeanName, descriptor)
                && isTypeCompatible(beanFactory, fieldBeanName, definition)) {
            log.warn("[JustMock] Type scan did not find a candidate for {}; "
                            + "using field-name fallback '{}' (resolved bean '{}')",
                    definition.describe(), fieldName, fieldBeanName);
            return fieldBeanName;
        }

        String defaultBeanName = generateBeanName(definition.getType());
        if (!defaultBeanName.equals(fieldName)) {
            String defaultBean = findExistingBeanDefinition(registry, beanFactory, defaultBeanName);
            if (defaultBean != null
                    && beanFactory.isAutowireCandidate(defaultBean, descriptor)
                    && isTypeCompatible(beanFactory, defaultBean, definition)) {
                log.warn("[JustMock] Type scan did not find a candidate for {}; "
                                + "using default bean-name fallback '{}' (resolved bean '{}')",
                        definition.describe(), defaultBeanName, defaultBean);
                return defaultBean;
            }
        }
        return null;
    }

    private boolean isTypeCompatible(ConfigurableListableBeanFactory beanFactory,
                                     String beanName, JustMockDefinition definition) {
        Class<?> beanType = safeBeanType(beanFactory, beanName);
        return beanType == null || definition.getType().isAssignableFrom(beanType);
    }

    /**
     * FeignClientFactoryBean 等类型可能不在测试 classpath；eager class-load 会抛
     * {@code CannotLoadBeanClassException}，这里按“类型未知”处理以免挡住替换。
     */
    private static Class<?> safeBeanType(ConfigurableListableBeanFactory beanFactory, String beanName) {
        try {
            return beanFactory.getType(beanName, false);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String findExistingBeanDefinition(BeanDefinitionRegistry registry,
                                              ConfigurableListableBeanFactory beanFactory,
                                              String requestedName) {
        for (String candidate : registry.getBeanDefinitionNames()) {
            if (!isInternalBeanName(candidate)
                    && matchesName(beanFactory, candidate, requestedName)) {
                return candidate;
            }
        }
        return null;
    }

    private String requireCandidate(ConfigurableListableBeanFactory beanFactory,
                                    JustMockDefinition definition, Set<String> candidates,
                                    String requestedName) {
        for (String candidate : candidates) {
            if (matchesName(beanFactory, candidate, requestedName)) {
                return candidate;
            }
        }
        throw new IllegalStateException(label(definition) + " No bean named '" + requestedName + "' for "
                + definition.describe() + ". Candidates: " + candidates);
    }

    private boolean matchesName(ConfigurableListableBeanFactory beanFactory,
                                String candidate, String requestedName) {
        if (candidate.equals(requestedName)) {
            return true;
        }
        if (beanFactory == null) {
            return false;
        }
        for (String alias : beanFactory.getAliases(candidate)) {
            if (alias.equals(requestedName)) {
                return true;
            }
        }
        return false;
    }

    private IllegalStateException ambiguous(ConfigurableListableBeanFactory beanFactory,
                                            JustMockDefinition definition, Set<String> candidates) {
        java.util.List<JustMockBeanOrigins.Candidate> described = JustMockBeanOrigins.describeCandidates(
                beanFactory, candidates, java.util.Collections.<String, String>emptyMap());
        if (JustMockBeanOrigins.hasAutoConfiguration(described)) {
            return JustMockBeanOrigins.conflict(definition.getType(), described);
        }
        String annotation = definition.isSpy() ? "@JustSpy" : "@JustMock";
        return new IllegalStateException(label(definition) + " Multiple beans found for " + definition.describe()
                + ": " + candidates + ". Use " + annotation + "(name = \"...\") or @Qualifier.");
    }

    private static String label(JustMockDefinition definition) {
        return definition.isSpy() ? "[JustSpy]" : "[JustMock]";
    }

    private static IllegalStateException missingSpyBean(JustMockDefinition definition, Set<String> candidates) {
        return new IllegalStateException("[JustSpy] No existing bean found for " + definition.describe()
                + ". @JustSpy only wraps a bean that is already defined and does not create one."
                + " Type candidates: " + candidates);
    }

    private static IllegalStateException duplicateSpyTarget(String beanName,
                                                           JustMockDefinition first,
                                                           JustMockDefinition second) {
        return new IllegalStateException("[JustSpy] " + first.describe() + " and " + second.describe()
                + " both resolved to bean '" + beanName + "'. One bean can only be wrapped once.");
    }

    private void rejectSpyMockTypeConflicts(Set<JustMockDefinition> spies, Set<JustMockDefinition> mocks) {
        java.util.Map<Class<?>, java.util.List<JustMockDefinition>> mocksByType =
                groupByType(mocks);
        java.util.Map<Class<?>, java.util.List<JustMockDefinition>> spiesByType =
                groupByType(spies);
        for (java.util.Map.Entry<Class<?>, java.util.List<JustMockDefinition>> entry : spiesByType.entrySet()) {
            java.util.List<JustMockDefinition> mockSide = mocksByType.get(entry.getKey());
            if (mockSide == null || mockSide.isEmpty()) {
                continue;
            }
            throw JustMockBeanOrigins.spyAndMockConflict(entry.getKey(), entry.getValue(), mockSide);
        }
    }

    private static java.util.Map<Class<?>, java.util.List<JustMockDefinition>> groupByType(
            Set<JustMockDefinition> definitions) {
        java.util.Map<Class<?>, java.util.List<JustMockDefinition>> grouped =
                new java.util.LinkedHashMap<Class<?>, java.util.List<JustMockDefinition>>();
        for (JustMockDefinition definition : definitions) {
            java.util.List<JustMockDefinition> group = grouped.get(definition.getType());
            if (group == null) {
                group = new java.util.ArrayList<JustMockDefinition>();
                grouped.put(definition.getType(), group);
            }
            group.add(definition);
        }
        return grouped;
    }

    private String registerThreadScopedSpy(ConfigurableListableBeanFactory beanFactory,
                                           BeanDefinitionRegistry registry,
                                           JustMockDefinition definition,
                                           String publicName) {
        if (!registry.containsBeanDefinition(publicName)) {
            throw new IllegalStateException("[JustSpy] No bean definition named '" + publicName
                    + "' to wrap for " + definition.describe()
                    + ". @JustSpy does not create a bean.");
        }
        Class<?> mockType = resolveMockType(beanFactory, registry, publicName, definition);
        FinalMethodMockGuard.check(mockType);
        BeanDefinition original = registry.getBeanDefinition(publicName);
        String scopedTargetName = ScopedProxyUtils.getTargetBeanName(publicName);
        BeanDefinition originalScopedTarget = registry.containsBeanDefinition(scopedTargetName)
                ? registry.getBeanDefinition(scopedTargetName) : null;
        String hiddenName = spyTargetBeanName(publicName);

        AbstractBeanDefinition mockDef = createSpyBeanDefinition(mockType, beanFactory, hiddenName);
        copyAutowireMetadata(mockDef, original, originalScopedTarget);

        if (originalScopedTarget != null) {
            registry.removeBeanDefinition(scopedTargetName);
            String hiddenScopedName = ScopedProxyUtils.getTargetBeanName(hiddenName);
            retargetScopedProxy(original, hiddenScopedName);
            hideRealTarget(originalScopedTarget);
            registry.registerBeanDefinition(hiddenScopedName, originalScopedTarget);
        }

        BeanDefinitionHolder holder = new BeanDefinitionHolder(mockDef, publicName);
        BeanDefinitionHolder proxy = ScopedProxyUtils.createScopedProxy(holder, registry, true);
        if (proxy.getBeanDefinition() instanceof AbstractBeanDefinition) {
            copyAutowireMetadata((AbstractBeanDefinition) proxy.getBeanDefinition(),
                    original, originalScopedTarget);
        }

        registry.removeBeanDefinition(publicName);
        hideRealTarget(original);
        registry.registerBeanDefinition(hiddenName, original);
        registry.registerBeanDefinition(publicName, proxy.getBeanDefinition());
        log.info("[JustSpy] Registered thread-scoped spy for {} as '{}' delegating to '{}'",
                mockType.getSimpleName(), publicName, hiddenName);
        return publicName;
    }

    private void retargetScopedProxy(BeanDefinition definition, String hiddenScopedTargetName) {
        if (definition.getPropertyValues().contains("targetBeanName")) {
            definition.getPropertyValues().add("targetBeanName", hiddenScopedTargetName);
        }
    }

    private void hideRealTarget(BeanDefinition definition) {
        definition.setAutowireCandidate(false);
        definition.setPrimary(false);
        definition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
    }

    private <T> AbstractBeanDefinition createSpyBeanDefinition(final Class<T> mockType,
                                                               final ConfigurableListableBeanFactory beanFactory,
                                                               final String realBeanName) {
        return BeanDefinitionBuilder
                .genericBeanDefinition(mockType, () -> createDelegatingMock(mockType, beanFactory, realBeanName))
                .setScope("thread")
                .getBeanDefinition();
    }

    private <T> T createDelegatingMock(Class<T> mockType,
                                       ConfigurableListableBeanFactory beanFactory,
                                       String realBeanName) {
        Object realBean = beanFactory.getBean(realBeanName);
        if (!mockType.isInstance(realBean)) {
            throw new IllegalStateException("[JustSpy] Bean '" + realBeanName + "' is not a "
                    + mockType.getName() + "; cannot delegate unstubbed calls to it.");
        }
        return Mockito.mock(mockType, threadScopedMockSettings()
                .defaultAnswer(AdditionalAnswers.delegatesTo(realBean)));
    }

    private void markMockSource(BeanDefinitionRegistry registry, String beanName,
                                JustMockDefinition definition) {
        String source = definition.sourceLabel();
        markSourceAttribute(registry, beanName, source);
        markSourceAttribute(registry, ScopedProxyUtils.getTargetBeanName(beanName), source);
    }

    private void markSourceAttribute(BeanDefinitionRegistry registry, String beanName, String source) {
        if (registry.containsBeanDefinition(beanName)) {
            registry.getBeanDefinition(beanName)
                    .setAttribute(JustMockBeanOrigins.SOURCE_ATTRIBUTE, source);
        }
    }

    /**
     * {@code @JustMock} / {@code @ThreadScopedMock} 只替换自己选中的那个 Bean。
     * 同类型若还留下第三方 {@code *AutoConfiguration} 候选，按类型注入会变成
     * {@code NoUniqueBeanDefinitionException}。这里在 Context refresh 期间 fail-fast，
     * 不自动 exclude，也不改 {@code @Resource} 语义。
     */
    private void rejectMockAutoConfigurationConflicts(ConfigurableListableBeanFactory beanFactory,
                                                      java.util.Map<JustMockDefinition, String> bindings) {
        java.util.Map<String, String> mockSources = new java.util.LinkedHashMap<String, String>();
        for (java.util.Map.Entry<JustMockDefinition, String> entry : bindings.entrySet()) {
            mockSources.put(entry.getValue(), entry.getKey().sourceLabel());
        }
        Set<Class<?>> seenTypes = new LinkedHashSet<Class<?>>();
        for (JustMockDefinition definition : bindings.keySet()) {
            Class<?> type = definition.getType();
            if (!seenTypes.add(type)) {
                continue;
            }
            java.util.List<JustMockBeanOrigins.Candidate> candidates = JustMockBeanOrigins.describeCandidates(
                    beanFactory, collectLogicalCandidates(beanFactory, type, false), mockSources);
            if (JustMockBeanOrigins.hasMockAndAutoConfiguration(candidates)) {
                throw JustMockBeanOrigins.conflict(type, candidates);
            }
        }
    }

    private String generateBeanName(Class<?> type) {
        String simpleName = type.getSimpleName();
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }

    private String generateAvailableBeanName(Class<?> type,
                                             ConfigurableListableBeanFactory beanFactory,
                                             BeanDefinitionRegistry registry) {
        String baseName = generateBeanName(type);
        if (!registry.containsBeanDefinition(baseName)
                && !registry.isAlias(baseName)
                && !beanFactory.containsSingleton(baseName)) {
            return baseName;
        }
        return BeanDefinitionReaderUtils.uniqueBeanName(baseName, registry);
    }

    @SuppressWarnings("unchecked")
    private <T> AbstractBeanDefinition createMockBeanDefinition(Class<T> mockType) {
        return BeanDefinitionBuilder
                .genericBeanDefinition(mockType, () -> Mockito.mock(mockType, threadScopedMockSettings()))
                .setScope("thread")
                .getBeanDefinition();
    }

    /**
     * {@code AbstractAdvisingBeanPostProcessor} 与 {@code AbstractAutoProxyCreator}
     * 会跳过 {@link AopInfrastructureBean}。线程作用域 mock 带上这个标记后，
     * {@code @Repository} 的异常转换、事务自动代理等不会再包住 mock 本身。
     * 未 stub 的 {@code @JustSpy} 调用因此只经过真实 Bean 上的那一层 advisor。
     * 隐藏真实 Bean 仍是普通实例，继续按原定义被自动代理。
     */
    private static MockSettings threadScopedMockSettings() {
        return Mockito.withSettings().extraInterfaces(AopInfrastructureBean.class);
    }

    private void copyAutowireMetadata(AbstractBeanDefinition target,
                                      BeanDefinition logicalDefinition,
                                      BeanDefinition scopedTargetDefinition) {
        if (logicalDefinition != null) {
            target.setPrimary(logicalDefinition.isPrimary());
            target.setAutowireCandidate(logicalDefinition.isAutowireCandidate());
            copyQualifiers(target, logicalDefinition);
        }
        copyQualifiers(target, scopedTargetDefinition);
    }

    private void copyQualifiers(AbstractBeanDefinition target, BeanDefinition source) {
        if (source instanceof AbstractBeanDefinition) {
            target.copyQualifiersFrom((AbstractBeanDefinition) source);
        }
    }
}

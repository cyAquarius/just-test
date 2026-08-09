package com.just.test.smarttest.mock;

import com.just.test.smarttest.annotation.ThreadScopedMock;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.core.type.MethodMetadata;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 将 {@code @SmartMock} 声明的类型 + {@code @ThreadScopedMock} 标记的 @Bean 方法
 * 统一替换为 thread-scoped + ScopedProxy 的 mock bean。
 *
 * <p>对每个 mock 类型：找到现有 bean → 移除 → 注册 thread-scoped mock → 包装 ScopedProxy。
 * 原 bean 名称指向 proxy，每次方法调用委托给当前线程的 mock 实例。</p>
 *
 * <p>来源：
 * <ol>
 *   <li>{@code mockTypes} —— 测试类字段 {@code @SmartMock} 扫描所得（per-test 差异化 mock）</li>
 *   <li>{@code @ThreadScopedMock} —— Configuration 类 {@code @Bean} 方法标记（全局外部依赖 mock）</li>
 * </ol>
 * </p>
 */
class SmartMockPostProcessor implements BeanFactoryPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(SmartMockPostProcessor.class);

    private final Set<Class<?>> mockTypes;

    SmartMockPostProcessor(Set<Class<?>> mockTypes) {
        this.mockTypes = mockTypes;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        if (!(beanFactory instanceof BeanDefinitionRegistry)) {
            log.warn("[SmartMock] BeanFactory is not a BeanDefinitionRegistry, skipping");
            return;
        }
        BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;

        // 合并 @SmartMock 字段类型 + @ThreadScopedMock @Bean 方法返回类型
        Set<Class<?>> allMockTypes = new LinkedHashSet<>(mockTypes);
        allMockTypes.addAll(collectThreadScopedMockTypes(beanFactory));

        Set<String> registeredBeanNames = new LinkedHashSet<>();
        for (Class<?> mockType : allMockTypes) {
            String beanName = registerThreadScopedMock(beanFactory, registry, mockType);
            if (beanName != null) {
                registeredBeanNames.add(beanName);
            }
        }

        // 注册 Registry bean，供 SmartTestExtension 在 case 开始时预热所有 thread-scoped mock
        registerRegistryBean(registry, registeredBeanNames);
    }

    private void registerRegistryBean(BeanDefinitionRegistry registry, Set<String> beanNames) {
        Set<String> snapshot = new LinkedHashSet<>(beanNames);
        BeanDefinition registryDef = BeanDefinitionBuilder
                .genericBeanDefinition(ThreadScopedMockRegistry.class,
                        () -> new ThreadScopedMockRegistry(snapshot))
                .getBeanDefinition();
        registry.registerBeanDefinition("smartMockThreadScopedMockRegistry", registryDef);
        log.info("[SmartMock] Registered ThreadScopedMockRegistry with {} beans", snapshot.size());
    }

    /**
     * 扫描所有 BeanDefinition，找出工厂方法带 {@link ThreadScopedMock} 注解的类型。
     *
     * <p>Spring 处理 {@code @Configuration} 时，每个 {@code @Bean} 方法会产生
     * {@code AnnotatedBeanDefinition}，通过 {@code getFactoryMethodMetadata()} 可读取方法级注解。</p>
     */
    private Set<Class<?>> collectThreadScopedMockTypes(ConfigurableListableBeanFactory beanFactory) {
        Set<Class<?>> types = new LinkedHashSet<>();
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
                log.warn("[SmartMock] @ThreadScopedMock bean '{}' has null type, skipping", beanName);
                continue;
            }
            types.add(type);
            log.debug("[SmartMock] Discovered @ThreadScopedMock bean '{}' of type {}", beanName, type.getSimpleName());
        }
        return types;
    }

    private String registerThreadScopedMock(ConfigurableListableBeanFactory beanFactory,
                                            BeanDefinitionRegistry registry,
                                            Class<?> mockType) {
        // 找到现有 bean 名称
        String[] beanNames = beanFactory.getBeanNamesForType(mockType, true, false);
        if (beanNames.length == 0) {
            log.warn("[SmartMock] No existing bean found for type {}, registering new mock", mockType.getName());
            beanNames = new String[]{generateBeanName(mockType)};
        } else if (beanNames.length > 1) {
            throw new IllegalStateException(String.format(
                    "[SmartMock] Multiple beans found for type %s: %s. "
                            + "SmartMock cannot choose a target safely.",
                    mockType.getName(), java.util.Arrays.toString(beanNames)));
        } else {
            String name = beanNames[0];
            if (registry.containsBeanDefinition(name)) {
                registry.removeBeanDefinition(name);
                log.debug("[SmartMock] Removed existing bean definition: {}", name);
            }
        }

        String targetBeanName = beanNames[0];
        String innerBeanName = "smartMock.target." + targetBeanName;

        // 注册 thread-scoped mock bean
        BeanDefinition mockDef = createMockBeanDefinition(mockType);
        registry.registerBeanDefinition(innerBeanName, mockDef);

        // 包装 ScopedProxy，原 bean 名称指向 proxy
        BeanDefinitionHolder holder = new BeanDefinitionHolder(mockDef, innerBeanName);
        BeanDefinitionHolder proxy = ScopedProxyUtils.createScopedProxy(holder, registry, true);
        registry.registerBeanDefinition(targetBeanName, proxy.getBeanDefinition());

        log.info("[SmartMock] Registered thread-scoped mock for {} as '{}'", mockType.getSimpleName(), targetBeanName);
        return targetBeanName;
    }

    private String generateBeanName(Class<?> type) {
        String simpleName = type.getSimpleName();
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }

    @SuppressWarnings("unchecked")
    private <T> BeanDefinition createMockBeanDefinition(Class<T> mockType) {
        return BeanDefinitionBuilder
                .genericBeanDefinition(mockType, () -> Mockito.mock(mockType))
                .setScope("thread")
                .getBeanDefinition();
    }
}

package com.just.test.internal.mock;

import com.just.test.internal.config.JustTestDataSourceConfig;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.MergedContextConfiguration;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 将 {@link JustMockPostProcessor} 注册到 ApplicationContext。
 *
 * <p>equals/hashCode 只计入会改变 Bean 定义的 {@code @JustMock} 与全部 {@code @JustSpy}。
 * 目标已经是 {@code @ThreadScopedMock} 或 Feign auto-mock 的 {@code @JustMock} 只绑定字段，
 * 不进入缓存 key：不同子集可以共享 Context。真实 Bean 的 {@code @JustMock} 仍会拆开 Context。</p>
 *
 * <p>这里只隔离 JustTest 自己拥有的资源；业务 JVM 静态状态不属于
 * ApplicationContext，不能通过 Context 创建锁获得通用的并发隔离。</p>
 */
class JustMockContextCustomizer implements ContextCustomizer {

    private final Set<JustMockDefinition> mockDefinitions;
    private final Set<JustMockDefinition> spyDefinitions;
    private final Set<JustMockDefinition> cacheKeyMocks;

    JustMockContextCustomizer(Set<JustMockDefinition> mockDefinitions) {
        this(mockDefinitions, Collections.<JustMockDefinition>emptySet());
    }

    JustMockContextCustomizer(Set<JustMockDefinition> mockDefinitions,
                              Set<JustMockDefinition> spyDefinitions) {
        this(mockDefinitions, spyDefinitions, mockDefinitions);
    }

    private JustMockContextCustomizer(Set<JustMockDefinition> mockDefinitions,
                                      Set<JustMockDefinition> spyDefinitions,
                                      Set<JustMockDefinition> cacheKeyMocks) {
        this.mockDefinitions = mockDefinitions;
        this.spyDefinitions = spyDefinitions;
        this.cacheKeyMocks = cacheKeyMocks;
    }

    static JustMockContextCustomizer forTestClass(Set<JustMockDefinition> mockDefinitions,
                                                   Set<JustMockDefinition> spyDefinitions,
                                                   Class<?> testClass,
                                                   List<ContextConfigurationAttributes> attributes) {
        return new JustMockContextCustomizer(
                mockDefinitions,
                spyDefinitions,
                JustMockCacheKey.affecting(testClass, attributes, mockDefinitions, spyDefinitions));
    }

    @Override
    public void customizeContext(ConfigurableApplicationContext context,
                                 MergedContextConfiguration mergedConfig) {
        registerJustTestConfiguration(context);
        JustMockPostProcessor postProcessor = new JustMockPostProcessor(mockDefinitions, spyDefinitions);
        // 仅通过 addBeanFactoryPostProcessor 注册一次。
        // 之前同时 registerSingleton + addBeanFactoryPostProcessor 会让 Spring 执行 PostProcessor 两次
        // （一次作为 BeanFactoryPostProcessor bean,一次来自 context.beanFactoryPostProcessors 列表),
        // 导致 mock bean 名字前缀累积(scopedTarget.justMock.target.justMock.target.xxx),
        // 先于第二次 run 就被实例化的业务 bean 持有旧 ScopedProxy,运行时 ScopeMap 查不到导致 mock 失效(偶发)。
        context.addBeanFactoryPostProcessor(postProcessor);
    }

    private static void registerJustTestConfiguration(ConfigurableApplicationContext context) {
        if (!(context.getBeanFactory() instanceof BeanDefinitionRegistry)) {
            throw new IllegalStateException("[JustTest] ApplicationContext does not support bean definitions");
        }
        BeanDefinitionRegistry registry = (BeanDefinitionRegistry) context.getBeanFactory();
        if (!registry.containsBeanDefinition("justTestDataSourceConfig")) {
            new AnnotatedBeanDefinitionReader(registry).register(JustTestDataSourceConfig.class);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JustMockContextCustomizer that = (JustMockContextCustomizer) o;
        return Objects.equals(cacheKeyMocks, that.cacheKeyMocks)
                && Objects.equals(spyDefinitions, that.spyDefinitions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cacheKeyMocks, spyDefinitions);
    }
}

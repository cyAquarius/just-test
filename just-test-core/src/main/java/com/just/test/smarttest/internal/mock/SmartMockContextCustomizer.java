package com.just.test.smarttest.internal.mock;

import com.just.test.smarttest.internal.config.SmartTestDataSourceConfig;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.MergedContextConfiguration;

import java.util.Objects;
import java.util.Set;

/**
 * 将 {@link SmartMockPostProcessor} 注册到 ApplicationContext。
 *
 * <p>equals/hashCode 基于影响 Bean 选择的 mock 语义，决定 Spring Context 缓存 key：
 * 相同 mock 组合共享 Context，不同组合独立 Context。</p>
 *
 * <p>这里只隔离 SmartTest 自己拥有的资源；业务 JVM 静态状态不属于
 * ApplicationContext，不能通过 Context 创建锁获得通用的并发隔离。</p>
 */
class SmartMockContextCustomizer implements ContextCustomizer {

    private final Set<SmartMockDefinition> mockDefinitions;

    SmartMockContextCustomizer(Set<SmartMockDefinition> mockDefinitions) {
        this.mockDefinitions = mockDefinitions;
    }

    @Override
    public void customizeContext(ConfigurableApplicationContext context,
                                 MergedContextConfiguration mergedConfig) {
        registerSmartTestConfiguration(context);
        SmartMockPostProcessor postProcessor = new SmartMockPostProcessor(mockDefinitions);
        // 仅通过 addBeanFactoryPostProcessor 注册一次。
        // 之前同时 registerSingleton + addBeanFactoryPostProcessor 会让 Spring 执行 PostProcessor 两次
        // （一次作为 BeanFactoryPostProcessor bean,一次来自 context.beanFactoryPostProcessors 列表),
        // 导致 mock bean 名字前缀累积(scopedTarget.smartMock.target.smartMock.target.xxx),
        // 先于第二次 run 就被实例化的业务 bean 持有旧 ScopedProxy,运行时 ScopeMap 查不到导致 mock 失效(偶发)。
        context.addBeanFactoryPostProcessor(postProcessor);
    }

    private static void registerSmartTestConfiguration(ConfigurableApplicationContext context) {
        if (!(context.getBeanFactory() instanceof BeanDefinitionRegistry)) {
            throw new IllegalStateException("[SmartTest] ApplicationContext does not support bean definitions");
        }
        BeanDefinitionRegistry registry = (BeanDefinitionRegistry) context.getBeanFactory();
        if (!registry.containsBeanDefinition("smartTestDataSourceConfig")) {
            new AnnotatedBeanDefinitionReader(registry).register(SmartTestDataSourceConfig.class);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SmartMockContextCustomizer that = (SmartMockContextCustomizer) o;
        return Objects.equals(mockDefinitions, that.mockDefinitions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mockDefinitions);
    }
}

package com.just.test.smarttest.mock;

import com.just.test.smarttest.config.SmartTestDataSourceConfig;
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
 * <p>equals/hashCode 基于 mock 类型集合，决定 Spring Context 缓存 key：
 * 相同 mock 组合共享 Context，不同组合独立 Context。</p>
 *
 * <p><b>Context 创建串行化</b>：通过全局锁确保同一时刻只有一个 Context 在初始化，
 * 解决 static 工厂注册表（如 GenericRegistry）
 * 在多 Context 并行初始化时的 @PostConstruct 竞争覆盖问题。</p>
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

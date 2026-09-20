package com.just.test.smarttest.internal.project;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * 在不破坏现有 {@code @Primary} 语义的前提下，为 SmartTest 主数据源 / 事务管理器
 * 注册应用常用的次要 Bean 名（{@code dataSource}、{@code transactionManager}）。
 *
 * <p>目标名已被 Bean 定义或别名占用时跳过，避免与双数据源契约冲突。</p>
 */
public class SmartTestProjectDataSourceAliasPostProcessor implements BeanDefinitionRegistryPostProcessor {

    static final String BEAN_NAME = "smartTestDataSourceAliasPostProcessor";

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        aliasIfAbsent(registry, "smartTestDataSource", "dataSource");
        aliasIfAbsent(registry, "smartTestTransactionManager", "transactionManager");
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // aliases are registered against the definition registry
    }

    static void register(BeanDefinitionRegistry registry) {
        if (registry.containsBeanDefinition(BEAN_NAME)) {
            return;
        }
        registry.registerBeanDefinition(BEAN_NAME,
                new RootBeanDefinition(SmartTestProjectDataSourceAliasPostProcessor.class));
    }

    static void aliasIfAbsent(BeanDefinitionRegistry registry, String canonicalName, String alias) {
        if (!registry.containsBeanDefinition(canonicalName)) {
            return;
        }
        if (registry.containsBeanDefinition(alias) || registry.isAlias(alias)) {
            return;
        }
        registry.registerAlias(canonicalName, alias);
    }
}

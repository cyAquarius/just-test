package com.just.test.smarttest.internal.project;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartTestProjectDataSourceAliasPostProcessorTest {

    @Test
    void aliasesMissingSecondaryNames() {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        registry.registerBeanDefinition("smartTestDataSource", new RootBeanDefinition(Object.class));
        registry.registerBeanDefinition("smartTestTransactionManager", new RootBeanDefinition(Object.class));

        SmartTestProjectDataSourceAliasPostProcessor processor =
                new SmartTestProjectDataSourceAliasPostProcessor();
        processor.postProcessBeanDefinitionRegistry(registry);

        assertTrue(registry.isAlias("dataSource"));
        assertTrue(registry.isAlias("transactionManager"));
    }

    @Test
    void skipsWhenSecondaryNameAlreadyExists() {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        registry.registerBeanDefinition("smartTestDataSource", new RootBeanDefinition(Object.class));
        registry.registerBeanDefinition("dataSource", new RootBeanDefinition(Object.class));

        SmartTestProjectDataSourceAliasPostProcessor.aliasIfAbsent(
                registry, "smartTestDataSource", "dataSource");

        assertFalse(registry.isAlias("dataSource"));
        assertTrue(registry.containsBeanDefinition("dataSource"));
    }
}

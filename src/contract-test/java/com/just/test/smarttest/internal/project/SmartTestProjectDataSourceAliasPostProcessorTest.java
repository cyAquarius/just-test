package com.just.test.smarttest.internal.project;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
        assertSame(registry.getBean("smartTestDataSource"), registry.getBean("dataSource"));
        assertSame(registry.getBean("smartTestTransactionManager"), registry.getBean("transactionManager"));
    }

    @Test
    void registersExtraAliasesAndKeepsDefaults() {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        registry.registerBeanDefinition("smartTestDataSource", new RootBeanDefinition(Object.class));
        registry.registerBeanDefinition("smartTestTransactionManager", new RootBeanDefinition(Object.class));

        SmartTestProjectDataSourceAliasPostProcessor processor =
                new SmartTestProjectDataSourceAliasPostProcessor(
                        new String[] {" masterDataSource ", "", "masterDataSource", "legacyDataSource"},
                        new String[] {"masterDataTransactionManager", "masterDataTransactionManager"});
        processor.postProcessBeanDefinitionRegistry(registry);

        assertTrue(registry.isAlias("dataSource"));
        assertTrue(registry.isAlias("masterDataSource"));
        assertTrue(registry.isAlias("legacyDataSource"));
        assertTrue(registry.isAlias("transactionManager"));
        assertTrue(registry.isAlias("masterDataTransactionManager"));
        assertSame(registry.getBean("smartTestDataSource"), registry.getBean("masterDataSource"));
        assertSame(registry.getBean("smartTestTransactionManager"),
                registry.getBean("masterDataTransactionManager"));
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

    @Test
    void skipsExtraAliasWhenBeanDefinitionExistsButStillAppliesDefaults() {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        registry.registerBeanDefinition("smartTestDataSource", new RootBeanDefinition(Object.class));
        registry.registerBeanDefinition("smartTestTransactionManager", new RootBeanDefinition(Object.class));
        registry.registerBeanDefinition("masterDataSource", new RootBeanDefinition(Object.class));

        SmartTestProjectDataSourceAliasPostProcessor processor =
                new SmartTestProjectDataSourceAliasPostProcessor(
                        new String[] {"masterDataSource", "legacyDataSource"},
                        new String[] {"masterDataTransactionManager"});
        processor.postProcessBeanDefinitionRegistry(registry);

        assertTrue(registry.isAlias("dataSource"));
        assertTrue(registry.isAlias("legacyDataSource"));
        assertFalse(registry.isAlias("masterDataSource"));
        assertTrue(registry.containsBeanDefinition("masterDataSource"));
        assertTrue(registry.isAlias("transactionManager"));
        assertTrue(registry.isAlias("masterDataTransactionManager"));
        assertSame(registry.getBean("smartTestDataSource"), registry.getBean("legacyDataSource"));
    }

    @Test
    void skipsWhenNameAlreadyAliasedToAnotherBean() {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        registry.registerBeanDefinition("smartTestDataSource", new RootBeanDefinition(Object.class));
        registry.registerBeanDefinition("otherDataSource", new RootBeanDefinition(Object.class));
        registry.registerAlias("otherDataSource", "masterDataSource");

        SmartTestProjectDataSourceAliasPostProcessor.aliasIfAbsent(
                registry, "smartTestDataSource", "masterDataSource");

        assertTrue(registry.isAlias("masterDataSource"));
        assertFalse(Arrays.asList(registry.getAliases("smartTestDataSource")).contains("masterDataSource"));
        assertTrue(Arrays.asList(registry.getAliases("otherDataSource")).contains("masterDataSource"));
    }

    @Test
    void uniqueNonBlankTrimsIgnoresBlanksAndDedups() {
        assertArrayEquals(
                new String[] {"masterDataSource", "legacyDataSource"},
                SmartTestProjectDataSourceAliasPostProcessor.uniqueNonBlank(
                        new String[] {" masterDataSource ", "", "masterDataSource", "legacyDataSource", "  "}));
        assertArrayEquals(new String[0], SmartTestProjectDataSourceAliasPostProcessor.uniqueNonBlank(null));
        assertArrayEquals(new String[0],
                SmartTestProjectDataSourceAliasPostProcessor.uniqueNonBlank(new String[0]));
    }

    @Test
    void registerStoresExtraAliasesAndAppliesThem() {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        registry.registerBeanDefinition("smartTestDataSource", new RootBeanDefinition(Object.class));
        registry.registerBeanDefinition("smartTestTransactionManager", new RootBeanDefinition(Object.class));

        SmartTestProjectDataSourceAliasPostProcessor.register(
                registry,
                new String[] {" masterDataSource ", "masterDataSource"},
                new String[] {"masterDataTransactionManager"});

        RootBeanDefinition definition = (RootBeanDefinition) registry.getBeanDefinition(
                SmartTestProjectDataSourceAliasPostProcessor.BEAN_NAME);
        ConstructorArgumentValues args = definition.getConstructorArgumentValues();
        assertArrayEquals(new String[] {"masterDataSource"},
                (String[]) args.getIndexedArgumentValue(0, String[].class).getValue());
        assertArrayEquals(new String[] {"masterDataTransactionManager"},
                (String[]) args.getIndexedArgumentValue(1, String[].class).getValue());

        SmartTestProjectDataSourceAliasPostProcessor processor = registry.getBean(
                SmartTestProjectDataSourceAliasPostProcessor.BEAN_NAME,
                SmartTestProjectDataSourceAliasPostProcessor.class);
        processor.postProcessBeanDefinitionRegistry(registry);

        assertTrue(registry.isAlias("dataSource"));
        assertTrue(registry.isAlias("masterDataSource"));
        assertTrue(registry.isAlias("masterDataTransactionManager"));
    }
}

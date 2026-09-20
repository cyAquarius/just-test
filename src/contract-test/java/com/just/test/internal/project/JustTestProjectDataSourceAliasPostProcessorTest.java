package com.just.test.internal.project;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JustTestProjectDataSourceAliasPostProcessorTest {

    @Test
    void aliasesMissingSecondaryNames() {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        registry.registerBeanDefinition("justTestDataSource", new RootBeanDefinition(Object.class));
        registry.registerBeanDefinition("justTestTransactionManager", new RootBeanDefinition(Object.class));

        JustTestProjectDataSourceAliasPostProcessor processor =
                new JustTestProjectDataSourceAliasPostProcessor();
        processor.postProcessBeanDefinitionRegistry(registry);

        assertTrue(registry.isAlias("dataSource"));
        assertTrue(registry.isAlias("transactionManager"));
        assertSame(registry.getBean("justTestDataSource"), registry.getBean("dataSource"));
        assertSame(registry.getBean("justTestTransactionManager"), registry.getBean("transactionManager"));
    }

    @Test
    void registersExtraAliasesAndKeepsDefaults() {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        registry.registerBeanDefinition("justTestDataSource", new RootBeanDefinition(Object.class));
        registry.registerBeanDefinition("justTestTransactionManager", new RootBeanDefinition(Object.class));

        JustTestProjectDataSourceAliasPostProcessor processor =
                new JustTestProjectDataSourceAliasPostProcessor(
                        new String[] {" masterDataSource ", "", "masterDataSource", "legacyDataSource"},
                        new String[] {"masterDataTransactionManager", "masterDataTransactionManager"});
        processor.postProcessBeanDefinitionRegistry(registry);

        assertTrue(registry.isAlias("dataSource"));
        assertTrue(registry.isAlias("masterDataSource"));
        assertTrue(registry.isAlias("legacyDataSource"));
        assertTrue(registry.isAlias("transactionManager"));
        assertTrue(registry.isAlias("masterDataTransactionManager"));
        assertSame(registry.getBean("justTestDataSource"), registry.getBean("masterDataSource"));
        assertSame(registry.getBean("justTestTransactionManager"),
                registry.getBean("masterDataTransactionManager"));
    }

    @Test
    void skipsWhenSecondaryNameAlreadyExists() {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        registry.registerBeanDefinition("justTestDataSource", new RootBeanDefinition(Object.class));
        registry.registerBeanDefinition("dataSource", new RootBeanDefinition(Object.class));

        JustTestProjectDataSourceAliasPostProcessor.aliasIfAbsent(
                registry, "justTestDataSource", "dataSource");

        assertFalse(registry.isAlias("dataSource"));
        assertTrue(registry.containsBeanDefinition("dataSource"));
    }

    @Test
    void skipsExtraAliasWhenBeanDefinitionExistsButStillAppliesDefaults() {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        registry.registerBeanDefinition("justTestDataSource", new RootBeanDefinition(Object.class));
        registry.registerBeanDefinition("justTestTransactionManager", new RootBeanDefinition(Object.class));
        registry.registerBeanDefinition("masterDataSource", new RootBeanDefinition(Object.class));

        JustTestProjectDataSourceAliasPostProcessor processor =
                new JustTestProjectDataSourceAliasPostProcessor(
                        new String[] {"masterDataSource", "legacyDataSource"},
                        new String[] {"masterDataTransactionManager"});
        processor.postProcessBeanDefinitionRegistry(registry);

        assertTrue(registry.isAlias("dataSource"));
        assertTrue(registry.isAlias("legacyDataSource"));
        assertFalse(registry.isAlias("masterDataSource"));
        assertTrue(registry.containsBeanDefinition("masterDataSource"));
        assertTrue(registry.isAlias("transactionManager"));
        assertTrue(registry.isAlias("masterDataTransactionManager"));
        assertSame(registry.getBean("justTestDataSource"), registry.getBean("legacyDataSource"));
    }

    @Test
    void skipsWhenNameAlreadyAliasedToAnotherBean() {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        registry.registerBeanDefinition("justTestDataSource", new RootBeanDefinition(Object.class));
        registry.registerBeanDefinition("otherDataSource", new RootBeanDefinition(Object.class));
        registry.registerAlias("otherDataSource", "masterDataSource");

        JustTestProjectDataSourceAliasPostProcessor.aliasIfAbsent(
                registry, "justTestDataSource", "masterDataSource");

        assertTrue(registry.isAlias("masterDataSource"));
        assertFalse(Arrays.asList(registry.getAliases("justTestDataSource")).contains("masterDataSource"));
        assertTrue(Arrays.asList(registry.getAliases("otherDataSource")).contains("masterDataSource"));
    }

    @Test
    void uniqueNonBlankTrimsIgnoresBlanksAndDedups() {
        assertArrayEquals(
                new String[] {"masterDataSource", "legacyDataSource"},
                JustTestProjectDataSourceAliasPostProcessor.uniqueNonBlank(
                        new String[] {" masterDataSource ", "", "masterDataSource", "legacyDataSource", "  "}));
        assertArrayEquals(new String[0], JustTestProjectDataSourceAliasPostProcessor.uniqueNonBlank(null));
        assertArrayEquals(new String[0],
                JustTestProjectDataSourceAliasPostProcessor.uniqueNonBlank(new String[0]));
    }

    @Test
    void registerStoresExtraAliasesAndAppliesThem() {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        registry.registerBeanDefinition("justTestDataSource", new RootBeanDefinition(Object.class));
        registry.registerBeanDefinition("justTestTransactionManager", new RootBeanDefinition(Object.class));

        JustTestProjectDataSourceAliasPostProcessor.register(
                registry,
                new String[] {" masterDataSource ", "masterDataSource"},
                new String[] {"masterDataTransactionManager"});

        RootBeanDefinition definition = (RootBeanDefinition) registry.getBeanDefinition(
                JustTestProjectDataSourceAliasPostProcessor.BEAN_NAME);
        ConstructorArgumentValues args = definition.getConstructorArgumentValues();
        assertArrayEquals(new String[] {"masterDataSource"},
                (String[]) args.getIndexedArgumentValue(0, String[].class).getValue());
        assertArrayEquals(new String[] {"masterDataTransactionManager"},
                (String[]) args.getIndexedArgumentValue(1, String[].class).getValue());

        JustTestProjectDataSourceAliasPostProcessor processor = registry.getBean(
                JustTestProjectDataSourceAliasPostProcessor.BEAN_NAME,
                JustTestProjectDataSourceAliasPostProcessor.class);
        processor.postProcessBeanDefinitionRegistry(registry);

        assertTrue(registry.isAlias("dataSource"));
        assertTrue(registry.isAlias("masterDataSource"));
        assertTrue(registry.isAlias("masterDataTransactionManager"));
    }
}

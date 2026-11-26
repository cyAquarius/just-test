package com.just.test.internal.project;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class JustTestProjectMyBatisSupportTest {

    @Test
    void secondRegistrationKeepsTheFirstDefinitions() {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        registerAndRunPostProcessor(registry, "com.first.mapper");
        BeanDefinition factory = registry.getBeanDefinition(
                JustTestProjectMyBatisSupport.SQL_SESSION_FACTORY_BEAN_NAME);
        BeanDefinition template = registry.getBeanDefinition(
                JustTestProjectMyBatisSupport.SQL_SESSION_TEMPLATE_BEAN_NAME);
        BeanDefinition scanner = registry.getBeanDefinition(
                JustTestProjectMyBatisSupport.MAPPER_SCANNER_BEAN_NAME);

        registerAndRunPostProcessor(registry, "com.second.mapper");

        assertSame(factory, registry.getBeanDefinition(
                JustTestProjectMyBatisSupport.SQL_SESSION_FACTORY_BEAN_NAME));
        assertSame(template, registry.getBeanDefinition(
                JustTestProjectMyBatisSupport.SQL_SESSION_TEMPLATE_BEAN_NAME));
        assertSame(scanner, registry.getBeanDefinition(
                JustTestProjectMyBatisSupport.MAPPER_SCANNER_BEAN_NAME));
        assertEquals("com.first.mapper", scanner.getPropertyValues().get("basePackage"));
    }

    @Test
    void keepsAnApplicationSqlSessionFactory() {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        RootBeanDefinition existing = new RootBeanDefinition(Object.class);
        registry.registerBeanDefinition(
                JustTestProjectMyBatisSupport.SQL_SESSION_FACTORY_BEAN_NAME, existing);

        registerAndRunPostProcessor(registry, "com.example.mapper");

        assertSame(existing, registry.getBeanDefinition(
                JustTestProjectMyBatisSupport.SQL_SESSION_FACTORY_BEAN_NAME));
        assertEquals("com.example.mapper", registry.getBeanDefinition(
                JustTestProjectMyBatisSupport.MAPPER_SCANNER_BEAN_NAME).getPropertyValues().get("basePackage"));
    }

    private static void registerAndRunPostProcessor(DefaultListableBeanFactory registry, String mapperPackage) {
        JustTestProjectMyBatisSupport.register(registry, new String[] {mapperPackage},
                JustTestProjectMyBatisSupportTest.class.getClassLoader());
        registry.getBean(JustTestProjectMyBatisSupport.FACTORY_REGISTRAR_BEAN_NAME,
                        JustTestProjectMyBatisSupport.class)
                .postProcessBeanDefinitionRegistry(registry);
    }
}

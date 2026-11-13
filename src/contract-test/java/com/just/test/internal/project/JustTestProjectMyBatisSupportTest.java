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
        JustTestProjectMyBatisSupport.register(registry, new String[] {"com.first.mapper"});
        BeanDefinition factory = registry.getBeanDefinition(
                JustTestProjectMyBatisSupport.SQL_SESSION_FACTORY_BEAN_NAME);
        BeanDefinition template = registry.getBeanDefinition(
                JustTestProjectMyBatisSupport.SQL_SESSION_TEMPLATE_BEAN_NAME);
        BeanDefinition scanner = registry.getBeanDefinition(
                JustTestProjectMyBatisSupport.MAPPER_SCANNER_BEAN_NAME);

        JustTestProjectMyBatisSupport.register(registry, new String[] {"com.second.mapper"});

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

        JustTestProjectMyBatisSupport.register(registry, new String[] {"com.example.mapper"});

        assertSame(existing, registry.getBeanDefinition(
                JustTestProjectMyBatisSupport.SQL_SESSION_FACTORY_BEAN_NAME));
        assertEquals("com.example.mapper", registry.getBeanDefinition(
                JustTestProjectMyBatisSupport.MAPPER_SCANNER_BEAN_NAME).getPropertyValues().get("basePackage"));
    }
}

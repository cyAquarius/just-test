package com.just.test.internal.mock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JustMockBeanOriginsTest {

    @Test
    void parsesAutoConfigurationClassFromClasspathResource() {
        assertEquals("com.acme.LogAutoConfiguration",
                JustMockBeanOrigins.classNameFromResource(
                        "class path resource [com/acme/LogAutoConfiguration.class]"));
        assertTrue(JustMockBeanOrigins.isAutoConfigurationClassName(
                "com.acme.LogAutoConfiguration"));
        assertEquals("LogAutoConfiguration",
                JustMockBeanOrigins.simpleName("com.acme.LogAutoConfiguration"));
    }

    @Test
    void parsesAutoConfigurationClassFromJarResource() {
        assertEquals("com.acme.LogAutoConfiguration",
                JustMockBeanOrigins.classNameFromResource(
                        "URL [jar:file:/app.jar!/com/acme/LogAutoConfiguration.class]"));
    }

    @Test
    void ignoresNonAutoConfigurationResources() {
        assertFalse(JustMockBeanOrigins.isAutoConfigurationClassName(
                "com.example.DemoConfiguration"));
        assertEquals("com.example.DemoConfiguration",
                JustMockBeanOrigins.classNameFromResource(
                        "class path resource [com/example/DemoConfiguration.class]"));
    }

    @Test
    void describesResourceOriginAsAutoConfiguration() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        org.springframework.beans.factory.support.AbstractBeanDefinition definition =
                BeanDefinitionBuilder.genericBeanDefinition(Object.class).getBeanDefinition();
        definition.setResourceDescription(
                "class path resource [com/acme/LogAutoConfiguration.class]");
        beanFactory.registerBeanDefinition("dataAudit", definition);

        assertEquals("com.acme.LogAutoConfiguration",
                JustMockBeanOrigins.detectAutoConfigurationClass(beanFactory, "dataAudit"));

        List<JustMockBeanOrigins.Candidate> candidates = JustMockBeanOrigins.describeCandidates(
                beanFactory, Collections.singleton("dataAudit"),
                Collections.<String, String>emptyMap());
        assertEquals(1, candidates.size());
        assertTrue(candidates.get(0).autoConfiguration);
        assertEquals("com.acme.LogAutoConfiguration", candidates.get(0).origin);
    }
}

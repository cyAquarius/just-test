package com.just.test.smarttest.mock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartMockPostProcessorTest {

    @Test
    void failsForAmbiguousCandidates() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("firstClient", new SampleClient());
        beanFactory.registerSingleton("secondClient", new SampleClient());
        SmartMockPostProcessor postProcessor = new SmartMockPostProcessor(
                Collections.singleton(new SmartMockDefinition(SampleClient.class, "client", "", "", "test")));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> postProcessor.postProcessBeanFactory(beanFactory));

        assertTrue(failure.getMessage().contains("Multiple beans"));
    }

    @Test
    void selectsCandidateMatchingFieldName() {
        DefaultListableBeanFactory beanFactory = beanFactoryWithTwoCandidates();
        SmartMockPostProcessor postProcessor = new SmartMockPostProcessor(
                Collections.singleton(new SmartMockDefinition(SampleClient.class,
                        "secondClient", "", "", "test")));

        postProcessor.postProcessBeanFactory(beanFactory);

        assertTrue(beanFactory.containsBeanDefinition("secondClient"));
        assertTrue(beanFactory.containsBeanDefinition("scopedTarget.secondClient"));
        assertTrue(beanFactory.containsBeanDefinition("firstClient"));
    }

    @Test
    void selectsExplicitBeanName() {
        DefaultListableBeanFactory beanFactory = beanFactoryWithTwoCandidates();
        SmartMockPostProcessor postProcessor = new SmartMockPostProcessor(
                Collections.singleton(new SmartMockDefinition(SampleClient.class,
                        "client", "secondClient", "", "test")));

        postProcessor.postProcessBeanFactory(beanFactory);

        assertTrue(beanFactory.containsBeanDefinition("scopedTarget.secondClient"));
    }

    private DefaultListableBeanFactory beanFactoryWithTwoCandidates() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("firstClient", BeanDefinitionBuilder
                .genericBeanDefinition(SampleClient.class).getBeanDefinition());
        beanFactory.registerBeanDefinition("secondClient", BeanDefinitionBuilder
                .genericBeanDefinition(SampleClient.class).getBeanDefinition());
        return beanFactory;
    }

    private static class SampleClient {
    }
}

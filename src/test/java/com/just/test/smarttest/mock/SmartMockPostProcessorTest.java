package com.just.test.smarttest.mock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartMockPostProcessorTest {

    @Test
    void failsInsteadOfReplacingMultipleCandidates() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("firstClient", new SampleClient());
        beanFactory.registerSingleton("secondClient", new SampleClient());
        SmartMockPostProcessor postProcessor = new SmartMockPostProcessor(
                Collections.<Class<?>>singleton(SampleClient.class));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> postProcessor.postProcessBeanFactory(beanFactory));

        assertTrue(failure.getMessage().contains("Multiple beans"));
    }

    private static class SampleClient {
    }
}

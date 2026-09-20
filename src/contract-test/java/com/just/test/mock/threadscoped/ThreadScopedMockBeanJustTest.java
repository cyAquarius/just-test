package com.just.test.mock.threadscoped;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.annotation.ThreadScopedMock;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@JustTest
@ContextConfiguration(classes = ThreadScopedMockBeanJustTest.TestConfiguration.class)
class ThreadScopedMockBeanJustTest implements JustTestLifecycle {

    @Autowired
    private ExternalClient externalClient;

    @Autowired
    private ExternalClientConsumer consumer;

    @CaseSource
    void replacesConfigurationBeanWithStubbableMock(CaseContext context) {
        when(externalClient.call()).thenReturn("stubbed");
        assertEquals("stubbed", consumer.call());
    }

    interface ExternalClient {
        String call();
    }

    static class ExternalClientConsumer {
        private final ExternalClient externalClient;

        ExternalClientConsumer(ExternalClient externalClient) {
            this.externalClient = externalClient;
        }

        String call() {
            return externalClient.call();
        }
    }

    @Configuration
    static class TestConfiguration {
        @Bean
        @ThreadScopedMock
        ExternalClient externalClient() {
            return mock(ExternalClient.class);
        }

        @Bean
        ExternalClientConsumer externalClientConsumer(ExternalClient externalClient) {
            return new ExternalClientConsumer(externalClient);
        }
    }
}

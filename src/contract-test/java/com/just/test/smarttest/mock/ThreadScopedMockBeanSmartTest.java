package com.just.test.smarttest.mock;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.annotation.ThreadScopedMock;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.lifecycle.SmartTestLifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SmartTest
@ContextConfiguration(classes = ThreadScopedMockBeanSmartTest.TestConfiguration.class)
class ThreadScopedMockBeanSmartTest implements SmartTestLifecycle {

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

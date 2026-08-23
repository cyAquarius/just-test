package com.just.test.smarttest.mock;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartMock;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.lifecycle.SmartTestLifecycle;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@SmartTest
@ContextConfiguration(classes = SmartMockConcreteTypeSmartTest.TestConfiguration.class)
class SmartMockConcreteTypeSmartTest implements SmartTestLifecycle {

    @Autowired
    private ConcreteConsumer consumer;

    @SmartMock
    private Client client;

    @BeforeEach
    void configureMock() {
        when(client.call()).thenReturn("mocked");
    }

    @CaseSource
    void keepsConcreteInjectionPointResolvable(CaseContext context) {
        assertEquals("mocked", consumer.call());
    }

    interface Client {
        String call();
    }

    public static class ConcreteClient implements Client {
        @Override
        public String call() {
            return "real";
        }
    }

    static class ConcreteConsumer {
        private final ConcreteClient client;

        ConcreteConsumer(ConcreteClient client) {
            this.client = client;
        }

        String call() {
            return client.call();
        }
    }

    @Configuration
    static class TestConfiguration {
        @Bean
        ConcreteClient concreteClient() {
            return new ConcreteClient();
        }

        @Bean
        ConcreteConsumer concreteConsumer(ConcreteClient client) {
            return new ConcreteConsumer(client);
        }
    }
}

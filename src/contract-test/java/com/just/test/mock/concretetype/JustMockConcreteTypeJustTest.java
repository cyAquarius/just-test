package com.just.test.mock.concretetype;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@JustTest
@ContextConfiguration(classes = JustMockConcreteTypeJustTest.TestConfiguration.class)
class JustMockConcreteTypeJustTest implements JustTestLifecycle {

    @Autowired
    private ConcreteConsumer consumer;

    @JustMock
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

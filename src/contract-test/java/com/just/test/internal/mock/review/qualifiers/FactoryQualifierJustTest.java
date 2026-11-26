package com.just.test.internal.mock.review.qualifiers;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustSpy;
import com.just.test.annotation.JustTest;
import com.just.test.annotation.ThreadScopedMock;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@JustTest
@ContextConfiguration(classes = FactoryQualifierJustTest.Config.class)
class FactoryQualifierJustTest implements JustTestLifecycle {

    @JustSpy
    private SpyGateway spyGateway;

    @JustMock
    private MockGateway mockGateway;

    @Autowired
    @Orders
    private SpyGateway qualifiedSpy;

    @Autowired
    @Qualifier("order")
    private MockGateway qualifiedMock;

    @Autowired
    @Qualifier("order")
    private ScopedGateway qualifiedScoped;

    @CaseSource
    void factoryMethodQualifiersSurviveAllThreeWrappers(CaseContext context) {
        when(spyGateway.read()).thenReturn("spy");
        when(mockGateway.read()).thenReturn("mock");
        when(qualifiedScoped.read()).thenReturn("scoped");
        assertEquals("spy", qualifiedSpy.read());
        assertEquals("mock", qualifiedMock.read());
        assertEquals("scoped", qualifiedScoped.read());
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @interface Orders {
    }

    interface SpyGateway {
        String read();
    }

    interface MockGateway {
        String read();
    }

    interface ScopedGateway {
        String read();
    }

    @Configuration
    static class Config {
        @Bean
        @Orders
        SpyGateway spyGateway() {
            return () -> "real";
        }

        @Bean
        @Qualifier("order")
        MockGateway mockGateway() {
            return () -> "real";
        }

        @Bean
        @ThreadScopedMock
        @Qualifier("order")
        ScopedGateway scopedGateway() {
            throw new AssertionError();
        }
    }
}

package com.just.test.mock.staticfield.instancecase;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustSpy;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

/**
 * Surefire 不直接执行本类。与 static 字段用例对照：实例 {@code @JustMock} / {@code @JustSpy}
 * 仍会启动并注入。
 */
@JustTest
@ContextConfiguration(classes = InstanceMockSpyFieldCases.TestConfiguration.class)
public class InstanceMockSpyFieldCases implements JustTestLifecycle {

    public static final AtomicReference<String> SEEN_GATEWAY = new AtomicReference<String>();
    public static final AtomicReference<String> SEEN_CLOCK = new AtomicReference<String>();

    @JustMock
    Gateway gateway;

    @JustSpy
    Clock clock;

    @CaseSource
    void injectsInstanceFields(CaseContext context) {
        assertTrue(mockingDetails(gateway).isMock());
        when(gateway.name()).thenReturn("stubbed");
        assertEquals("stubbed", gateway.name());

        assertTrue(mockingDetails(clock).isMock());
        doReturn("frozen").when(clock).now();
        assertEquals("frozen", clock.now());
        assertEquals("real-zone", clock.zone());

        SEEN_GATEWAY.set(gateway.name());
        SEEN_CLOCK.set(clock.now());
        context.setResult(gateway.name());
    }

    public interface Gateway {
        String name();
    }

    public static class Clock {
        public String now() {
            return "real-now";
        }

        public String zone() {
            return "real-zone";
        }
    }

    @Configuration
    public static class TestConfiguration {
        @Bean
        Clock clock() {
            return new Clock();
        }
    }
}

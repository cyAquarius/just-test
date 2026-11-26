package com.just.test.internal.mock.review.lifecycle;

import com.just.test.annotation.JustSpy;
import com.just.test.annotation.JustTestProject;
import com.just.test.lifecycle.JustTestLifecycle;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@JustTestProject(basePackages = "com.just.test.internal.mock.review.lifecycle.no_components",
        interceptMocks = LifecycleMockConfig.Intercepted.class)
public class LifecycleMockConfig {

    @Bean
    Counters counters() {
        return new Counters();
    }

    @Bean
    Spied spied(Counters counters) {
        return new Spied(counters.spyInit, counters.spyClose);
    }

    @Bean
    Intercepted intercepted(Counters counters) {
        return new Intercepted(counters.interceptInit, counters.interceptClose);
    }

    public static class Counters {
        final AtomicInteger spyInit = new AtomicInteger();
        final AtomicInteger spyClose = new AtomicInteger();
        final AtomicInteger interceptInit = new AtomicInteger();
        final AtomicInteger interceptClose = new AtomicInteger();

        public void assertRealBeansOpen() {
            assertEquals(1, spyInit.get());
            assertEquals(1, interceptInit.get());
            assertEquals(0, spyClose.get());
            assertEquals(0, interceptClose.get());
        }
    }

    public static class Resource implements InitializingBean, AutoCloseable {
        private final AtomicInteger init;
        private final AtomicInteger close;

        Resource(AtomicInteger init, AtomicInteger close) {
            this.init = init;
            this.close = close;
        }

        @Override
        public void afterPropertiesSet() {
            init.incrementAndGet();
        }

        @Override
        public void close() {
            close.incrementAndGet();
        }

        public String read() {
            return close.get() == 0 ? "open" : "closed";
        }
    }

    public static class Spied extends Resource {
        Spied(AtomicInteger init, AtomicInteger close) {
            super(init, close);
        }
    }

    public static class Intercepted extends Resource {
        Intercepted(AtomicInteger init, AtomicInteger close) {
            super(init, close);
        }
    }

    public abstract static class Support implements JustTestLifecycle {
        @JustSpy
        protected Spied spied;

        @Autowired
        protected Counters counters;
    }
}

package com.just.test.mock.staticfield.spycase;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustSpy;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Surefire 不直接执行本类。{@code StaticFieldIsolationTest} 断言 static {@code @JustSpy}
 * 在 BeforeAll 失败，Context 不会启动。
 */
@JustTest
@ContextConfiguration(classes = StaticJustSpyFieldCases.TestConfiguration.class)
public class StaticJustSpyFieldCases implements JustTestLifecycle {

    public static final AtomicInteger INVOCATIONS = new AtomicInteger();
    public static final AtomicInteger REFRESH_ATTEMPTS = new AtomicInteger();

    @JustSpy
    static Clock sharedClock;

    @CaseSource
    void run(CaseContext context) {
        INVOCATIONS.incrementAndGet();
        context.setResult(sharedClock.now());
    }

    public static class Clock {
        public String now() {
            return "real-now";
        }
    }

    @Configuration
    public static class TestConfiguration {
        @Bean
        Clock clock() {
            return new Clock();
        }

        @Bean
        static BeanDefinitionRegistryPostProcessor refreshAttemptProbe() {
            return new BeanDefinitionRegistryPostProcessor() {
                @Override
                public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
                    REFRESH_ATTEMPTS.incrementAndGet();
                }

                @Override
                public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
                        throws BeansException {
                }
            };
        }
    }
}

package com.just.test.spy.conflict;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustMock;
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
 * Surefire 不会执行本类（类名不以 Test 结尾）。由
 * {@link JustSpyConflictClassAbortTest} 通过 EngineTestKit 启动。
 */
@JustTest
@ContextConfiguration(classes = SpyMockConflictCases.TestConfiguration.class)
class SpyMockConflictCases implements JustTestLifecycle {

    static final AtomicInteger REFRESH_ATTEMPTS = new AtomicInteger();

    @JustMock
    private SharedClient mocked;

    @JustSpy
    private SharedClient spied;

    @CaseSource
    void run(CaseContext context) {
        context.setResult("should-not-run");
    }

    @Configuration
    static class TestConfiguration {
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

        @Bean
        SharedClient sharedClient() {
            return new SharedClient();
        }
    }

    public static class SharedClient {
        public String id() {
            return "real";
        }
    }
}

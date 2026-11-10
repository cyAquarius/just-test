package com.just.test.spy.missing;

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
 * Surefire 不会执行本类。由 {@link JustSpyMissingBeanTest} 通过 EngineTestKit 启动。
 */
@JustTest
@ContextConfiguration(classes = JustSpyMissingBeanCases.TestConfiguration.class)
class JustSpyMissingBeanCases implements JustTestLifecycle {

    static final AtomicInteger REFRESH_ATTEMPTS = new AtomicInteger();

    @JustSpy
    private MissingClient missingClient;

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
    }

    public interface MissingClient {
        String id();
    }
}

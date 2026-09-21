package com.just.test.mock.conflict;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.annotation.ThreadScopedMock;
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

import static org.mockito.Mockito.mock;

/**
 * Surefire 不会执行本类（类名不以 Test 结尾）。由
 * {@link JustMockConflictClassAbortTest} 通过 EngineTestKit 启动，
 * 证明 mock 与 {@code *AutoConfiguration} 冲突只在类级失败一次。
 */
@JustTest
@ContextConfiguration(classes = {
        MockAutoConfigurationConflictCases.MockConfig.class,
        MockAutoConfigurationConflictCases.SampleLogAutoConfiguration.class
})
class MockAutoConfigurationConflictCases implements JustTestLifecycle {

    static final AtomicInteger REFRESH_ATTEMPTS = new AtomicInteger();

    @CaseSource
    void run(CaseContext context) {
        context.setResult("should-not-run");
    }

    @Configuration
    static class MockConfig {
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
        @ThreadScopedMock
        ConflictingClient projectMock() {
            return mock(ConflictingClient.class);
        }
    }

    @Configuration
    static class SampleLogAutoConfiguration {
        @Bean
        ConflictingClient dataAudit() {
            return new ConflictingClient() {
                @Override
                public String id() {
                    return "production";
                }
            };
        }
    }

    interface ConflictingClient {
        String id();
    }
}

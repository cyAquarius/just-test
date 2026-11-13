package com.just.test.mock.threadscoped.body;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.annotation.ThreadScopedMock;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * 钉住当前语义：{@code @ThreadScopedMock} 的 {@code @Bean} 方法体不会执行。
 * 计数、自定义 {@code MockSettings} 和手写返回值都被忽略，注入的仍是框架创建的线程作用域 mock。
 */
@JustTest
@ContextConfiguration(classes = ThreadScopedMockBeanBodyJustTest.TestConfiguration.class)
class ThreadScopedMockBeanBodyJustTest implements JustTestLifecycle {

    static final AtomicInteger BODY_CALLS = new AtomicInteger();

    @Autowired
    private ExternalClient externalClient;

    @Autowired
    private ExternalClientConsumer consumer;

    @Autowired
    private ApplicationContext applicationContext;

    @CaseSource
    void ignoresBeanMethodBody(CaseContext context) throws Exception {
        assertEquals(0, BODY_CALLS.get());

        Object injected = currentTarget(externalClient);
        Object named = currentTarget(applicationContext.getBean("declaredClient"));
        assertSame(injected, named);
        assertTrue(mockingDetails(injected).isMock());
        assertTrue(injected instanceof AopInfrastructureBean);
        assertFalse(injected instanceof Marker);
        assertTrue(mockingDetails(injected).getMockCreationSettings().getMockName().isDefault());

        assertNull(externalClient.call());
        assertNull(consumer.call());
        when(externalClient.call()).thenReturn("stubbed");
        assertEquals("stubbed", consumer.call());
        assertEquals(0, BODY_CALLS.get());
    }

    private static Object currentTarget(Object bean) throws Exception {
        if (bean instanceof Advised) {
            return ((Advised) bean).getTargetSource().getTarget();
        }
        return bean;
    }

    interface ExternalClient {
        String call();
    }

    interface Marker {
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
        @Bean("declaredClient")
        @ThreadScopedMock
        ExternalClient ignoredBody() {
            BODY_CALLS.incrementAndGet();
            return mock(ExternalClient.class, withSettings()
                    .name("custom-name")
                    .extraInterfaces(Marker.class)
                    .defaultAnswer(invocation -> "custom-answer"));
        }

        @Bean
        ExternalClientConsumer externalClientConsumer(ExternalClient declaredClient) {
            return new ExternalClientConsumer(declaredClient);
        }
    }
}

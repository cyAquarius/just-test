package com.just.test.spy.transactional;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustSpy;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockingDetails;

/**
 * {@code @EnableTransactionManagement} 会给具体类上的 {@code @Transactional} 做自动代理。
 * 未 stub 的调用必须仍处在事务中；测试字段本身保持为可 stub 的 mock。
 */
@JustTest
@ContextConfiguration(classes = JustSpyTransactionalBeanJustTest.TestConfiguration.class)
class JustSpyTransactionalBeanJustTest implements JustTestLifecycle {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private OrderConsumer orderConsumer;

    @JustSpy
    private OrderService orderService;

    @CaseSource
    void stubbableFieldLeavesTransactionAdviceOnTheRealBean(CaseContext context) throws Exception {
        assertTrue(mockingDetails(orderService).isMock());
        assertFalse(AopUtils.isAopProxy(orderService));
        assertSame(orderService, scopedTarget("orderService"));

        doReturn("stub-title").when(orderService).title();

        assertEquals("stub-title", orderConsumer.title());
        assertEquals("in-tx", orderConsumer.status());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    private Object scopedTarget(String beanName) throws Exception {
        Object bean = applicationContext.getBean(beanName);
        assertTrue(bean instanceof Advised);
        Object target = ((Advised) bean).getTargetSource().getTarget();
        assertFalse(target instanceof Advised);
        return target;
    }

    public static class OrderService {
        @Transactional
        public String status() {
            return TransactionSynchronizationManager.isActualTransactionActive() ? "in-tx" : "no-tx";
        }

        @Transactional
        public String title() {
            return "real-title";
        }
    }

    static class OrderConsumer {
        private final OrderService orderService;

        OrderConsumer(OrderService orderService) {
            this.orderService = orderService;
        }

        String status() {
            return orderService.status();
        }

        String title() {
            return orderService.title();
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfiguration {
        @Bean(name = "orderService")
        OrderService orderService() {
            return new OrderService();
        }

        @Bean
        OrderConsumer orderConsumer(OrderService orderService) {
            return new OrderConsumer(orderService);
        }
    }
}

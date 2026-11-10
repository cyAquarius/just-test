package com.just.test.spy.aop;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustSpy;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.MatchAlwaysTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;

/**
 * 容器 Bean 本身就是带事务 advisor 的 AOP 代理。未 stub 的调用必须打到这个代理，
 * 而不是被拆开的 target，否则 {@code price()} 不会处于事务中。
 */
@JustTest
@ContextConfiguration(classes = JustSpyAdvisedBeanJustTest.TestConfiguration.class)
class JustSpyAdvisedBeanJustTest implements JustTestLifecycle {

    @Autowired
    private CatalogConsumer catalogConsumer;

    @JustSpy
    private CatalogService catalogService;

    @CaseSource
    void unstubbedCallGoesThroughTheAdvisor(CaseContext context) {
        doReturn("stub-title").when(catalogService).title();

        assertEquals("in-tx", catalogConsumer.price());
        assertEquals("stub-title", catalogConsumer.title());
    }

    public static class CatalogService {
        public String price() {
            return TransactionSynchronizationManager.isActualTransactionActive() ? "in-tx" : "no-tx";
        }

        public String title() {
            return "real-title";
        }
    }

    static class CatalogConsumer {
        private final CatalogService catalogService;

        CatalogConsumer(CatalogService catalogService) {
            this.catalogService = catalogService;
        }

        String price() {
            return catalogService.price();
        }

        String title() {
            return catalogService.title();
        }
    }

    @Configuration
    static class TestConfiguration {
        @Bean
        CatalogService catalogService(PlatformTransactionManager transactionManager) {
            CatalogService target = new CatalogService();
            ProxyFactory factory = new ProxyFactory(target);
            factory.setProxyTargetClass(true);
            factory.addAdvice(new TransactionInterceptor(
                    transactionManager, new MatchAlwaysTransactionAttributeSource()));
            return (CatalogService) factory.getProxy();
        }

        @Bean
        CatalogConsumer catalogConsumer(CatalogService catalogService) {
            return new CatalogConsumer(catalogService);
        }
    }
}

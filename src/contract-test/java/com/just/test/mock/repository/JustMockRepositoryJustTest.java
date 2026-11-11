package com.just.test.mock.repository;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.stereotype.Repository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockingDetails;

/**
 * {@code @JustMock} 替换 {@code @Repository} 实现时，线程作用域 mock 同样不能再被
 * {@link PersistenceExceptionTranslationPostProcessor} 包成 CGLIB advisor 代理。
 */
@JustTest
@ContextConfiguration(classes = JustMockRepositoryJustTest.TestConfiguration.class)
@TestPropertySource(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.dao.PersistenceExceptionTranslationAutoConfiguration")
class JustMockRepositoryJustTest implements JustTestLifecycle {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private FormReader formReader;

    @JustMock
    private FormDao formDao;

    @CaseSource
    void repositoryMockFieldStaysStubbable(CaseContext context) throws Exception {
        assertTrue(mockingDetails(formDao).isMock());
        assertFalse(AopUtils.isAopProxy(formDao));
        assertSame(formDao, scopedTarget("formDao"));

        doReturn("stub-label").when(formDao).label();

        assertEquals("stub-label", formReader.label());
        assertNull(formReader.code());
    }

    private Object scopedTarget(String beanName) throws Exception {
        Object bean = applicationContext.getBean(beanName);
        assertTrue(bean instanceof Advised);
        Object target = ((Advised) bean).getTargetSource().getTarget();
        assertFalse(target instanceof Advised);
        return target;
    }

    public interface FormDao {
        String label();

        String code();
    }

    @Repository
    public static class FormDaoImpl implements FormDao {
        @Override
        public String label() {
            return "real-label";
        }

        @Override
        public String code() {
            return "real-code";
        }
    }

    static class FormReader {
        private final FormDao formDao;

        FormReader(FormDao formDao) {
            this.formDao = formDao;
        }

        String label() {
            return formDao.label();
        }

        String code() {
            return formDao.code();
        }
    }

    @Configuration
    static class TestConfiguration {
        @Bean(name = "formDao")
        FormDaoImpl formDao() {
            return new FormDaoImpl();
        }

        @Bean
        FormReader formReader(FormDao formDao) {
            return new FormReader(formDao);
        }

        @Bean
        static PersistenceExceptionTranslationPostProcessor persistenceExceptionTranslationPostProcessor() {
            PersistenceExceptionTranslationPostProcessor processor =
                    new PersistenceExceptionTranslationPostProcessor();
            processor.setProxyTargetClass(true);
            return processor;
        }
    }
}

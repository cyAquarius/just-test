package com.just.test.spy.repository;

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
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.stereotype.Repository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockingDetails;

/**
 * {@code @Repository} 实现会被 {@link PersistenceExceptionTranslationPostProcessor}
 * 自动代理。线程作用域 delegating mock 不能再被包一层，测试字段必须能直接 stub；
 * 未 stub 的调用仍打到真实 Bean，异常转换只发生一次。
 */
@JustTest
@ContextConfiguration(classes = JustSpyRepositoryJustTest.TestConfiguration.class)
@TestPropertySource(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.dao.PersistenceExceptionTranslationAutoConfiguration")
class JustSpyRepositoryJustTest implements JustTestLifecycle {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private FormReader formReader;

    @JustSpy
    private FormDao formDao;

    @CaseSource
    void stubbableFieldLeavesRepositoryAdviceOnTheRealBean(CaseContext context) throws Exception {
        assertTrue(mockingDetails(formDao).isMock());
        assertFalse(AopUtils.isAopProxy(formDao));
        assertSame(formDao, scopedTarget("formDao"));

        doReturn("stub-label").when(formDao).label();

        assertEquals("stub-label", formReader.label());
        assertEquals("real-code", formReader.code());
        DataRetrievalFailureException translated = assertThrows(
                DataRetrievalFailureException.class, () -> formReader.lookup(1L));
        assertTrue(translated.getMessage().startsWith("once"), translated.getMessage());
        assertTrue(translated.getCause() instanceof RawPersistenceFailure);
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

        String lookup(Long id);
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

        @Override
        public String lookup(Long id) {
            throw new RawPersistenceFailure();
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

        String lookup(Long id) {
            return formDao.lookup(id);
        }
    }

    static class RawPersistenceFailure extends RuntimeException {
        RawPersistenceFailure() {
            super("raw");
        }
    }

    /**
     * 第一层 advisor 把原始异常译成 {@code once}。若 mock 再被包一层，外层会看到
     * 已经译过的异常并改写成 {@code twice}。
     */
    static class OnceThenTwiceTranslator implements PersistenceExceptionTranslator {
        @Override
        public DataAccessException translateExceptionIfPossible(RuntimeException ex) {
            if (ex instanceof DataRetrievalFailureException
                    && ex.getCause() instanceof RawPersistenceFailure) {
                return new DataRetrievalFailureException("twice", ex);
            }
            if (ex instanceof RawPersistenceFailure) {
                return new DataRetrievalFailureException("once", ex);
            }
            return null;
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
            // Boot 默认 spring.aop.proxy-target-class=true，@Repository 实现会被 CGLIB 代理。
            processor.setProxyTargetClass(true);
            return processor;
        }

        @Bean
        PersistenceExceptionTranslator onceThenTwiceTranslator() {
            return new OnceThenTwiceTranslator();
        }
    }
}

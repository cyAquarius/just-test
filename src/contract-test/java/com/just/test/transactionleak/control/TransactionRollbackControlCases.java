package com.just.test.transactionleak.control;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@JustTest
@Execution(ExecutionMode.SAME_THREAD)
@ContextConfiguration(classes = TransactionRollbackControlCases.TestConfiguration.class)
public class TransactionRollbackControlCases implements JustTestLifecycle {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private RecordService recordService;

    @CaseSource
    void run(CaseContext context) {
        if ("a-rollback".equals(context.getCaseName())) {
            TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
            try {
                throw new ExpectedBusinessException("validation failed after transaction start");
            } finally {
                transactionManager.rollback(status);
            }
        }

        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals("after-rollback", recordService.insertAndFind("after-rollback"));
    }

    @AfterAll
    static void transactionThreadStateIsClean() {
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    public static class ExpectedBusinessException extends RuntimeException {
        ExpectedBusinessException(String message) {
            super(message);
        }
    }

    interface RecordMapper {
        @Insert("INSERT INTO parallel_case_record(id, marker) VALUES (1, #{marker})")
        void insert(@Param("marker") String marker);

        @Select("SELECT marker FROM parallel_case_record WHERE id = 1")
        String findMarker();
    }

    static class RecordService {
        private final RecordMapper mapper;

        RecordService(RecordMapper mapper) {
            this.mapper = mapper;
        }

        @Transactional
        public String insertAndFind(String marker) {
            mapper.insert(marker);
            return mapper.findMarker();
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfiguration {
        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
            bean.setDataSource(dataSource);
            SqlSessionFactory factory = bean.getObject();
            factory.getConfiguration().addMapper(RecordMapper.class);
            return factory;
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory);
        }

        @Bean
        RecordMapper recordMapper(SqlSessionTemplate template) {
            return template.getMapper(RecordMapper.class);
        }

        @Bean
        RecordService recordService(RecordMapper mapper) {
            return new RecordService(mapper);
        }
    }
}

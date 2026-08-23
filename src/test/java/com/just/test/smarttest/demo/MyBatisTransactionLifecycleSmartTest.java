package com.just.test.smarttest.demo;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.lifecycle.SmartTestLifecycle;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SmartTest
@Execution(ExecutionMode.SAME_THREAD)
@ContextConfiguration(classes = MyBatisTransactionLifecycleSmartTest.TestConfiguration.class)
class MyBatisTransactionLifecycleSmartTest implements SmartTestLifecycle {

    private static final AtomicInteger EXECUTED_CASES = new AtomicInteger();
    private static final Set<String> EXECUTION_THREADS = ConcurrentHashMap.newKeySet();

    @Autowired
    private RecordService recordService;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @ParameterizedTest(name = "{0}")
    @CaseSource
    void releasesSpringAndMyBatisResourcesBetweenCases(CaseContext context) {
        EXECUTION_THREADS.add(Thread.currentThread().getName());
        EXECUTED_CASES.incrementAndGet();

        String marker = context.getString("marker");
        assertEquals(marker, recordService.insertAndFind(marker));
        assertFalse(TransactionSynchronizationManager.hasResource(dataSource));
        assertFalse(TransactionSynchronizationManager.hasResource(sqlSessionFactory));
    }

    @AfterAll
    static void verifiesBothCasesReusedOneExecutionThread() {
        assertEquals(2, EXECUTED_CASES.get());
        assertEquals(1, EXECUTION_THREADS.size());
    }

    interface RecordMapper {
        @Insert("INSERT INTO parallel_case_record(id, marker) VALUES (1, #{marker})")
        void insert(@Param("marker") String marker);

        @Select("SELECT marker FROM parallel_case_record WHERE id = 1")
        String findMarker();
    }

    static class RecordService {
        private final RecordMapper recordMapper;

        RecordService(RecordMapper recordMapper) {
            this.recordMapper = recordMapper;
        }

        @Transactional
        public String insertAndFind(String marker) {
            recordMapper.insert(marker);
            return recordMapper.findMarker();
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfiguration {

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
            sqlSessionFactory.getConfiguration().addMapper(RecordMapper.class);
            return sqlSessionFactory;
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
            return new SqlSessionTemplate(sqlSessionFactory);
        }

        @Bean
        RecordMapper recordMapper(SqlSessionTemplate sqlSessionTemplate) {
            return sqlSessionTemplate.getMapper(RecordMapper.class);
        }

        @Bean
        RecordService recordService(RecordMapper recordMapper) {
            return new RecordService(recordMapper);
        }
    }
}

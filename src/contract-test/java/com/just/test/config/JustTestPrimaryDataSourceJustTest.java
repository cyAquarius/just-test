package com.just.test.config;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ContextConfiguration;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

@JustTest
@ContextConfiguration(classes = {
        JustTestPrimaryDataSourceJustTest.TestApplication.class,
        JustTestPrimaryDataSourceJustTest.ProductionDataSourceConfig.class
})
class JustTestPrimaryDataSourceJustTest implements JustTestLifecycle {

    @Autowired
    @Qualifier("justTestDataSource")
    private DataSource justTestDataSource;

    @Autowired
    @Qualifier("productionDataSource")
    private DataSource productionDataSource;

    @Autowired
    private DataSourceTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @CaseSource
    void usesJustTestDataSource(CaseContext context) {
        assertSame(justTestDataSource, transactionManager.getDataSource());
        assertSame(justTestDataSource, jdbcTemplate.getDataSource());
        assertNotSame(productionDataSource, justTestDataSource);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @Configuration
    static class ProductionDataSourceConfig {
        @Bean
        @Primary
        DataSource productionDataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:production");
        }
    }
}

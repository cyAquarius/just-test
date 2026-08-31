package com.just.test.smarttest.config;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.lifecycle.SmartTestLifecycle;
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

@SmartTest
@ContextConfiguration(classes = {
        SmartTestPrimaryDataSourceSmartTest.TestApplication.class,
        SmartTestPrimaryDataSourceSmartTest.ProductionDataSourceConfig.class
})
class SmartTestPrimaryDataSourceSmartTest implements SmartTestLifecycle {

    @Autowired
    @Qualifier("smartTestDataSource")
    private DataSource smartTestDataSource;

    @Autowired
    @Qualifier("productionDataSource")
    private DataSource productionDataSource;

    @Autowired
    private DataSourceTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @CaseSource
    void usesSmartTestDataSource(CaseContext context) {
        assertSame(smartTestDataSource, transactionManager.getDataSource());
        assertSame(smartTestDataSource, jdbcTemplate.getDataSource());
        assertNotSame(productionDataSource, smartTestDataSource);
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

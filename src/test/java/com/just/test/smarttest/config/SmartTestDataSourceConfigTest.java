package com.just.test.smarttest.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SmartTestDataSourceConfigTest {

    @Test
    void defaultCaseUrlDoesNotKeepMemoryDatabaseAlive() {
        assertFalse(SmartTestDataSourceConfig.DEFAULT_H2_URL.contains("DB_CLOSE_DELAY=-1"));
    }

    @Test
    void internalInfrastructureUsesSmartTestDataSourceWhenAnotherPrimaryExists() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                SmartTestDataSourceConfig.class, ProductionDataSourceConfig.class);
        try {
            DataSource smartTestDataSource = context.getBean("smartTestDataSource", DataSource.class);
            DataSourceTransactionManager transactionManager = context.getBean(
                    "smartTestTransactionManager", DataSourceTransactionManager.class);
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);

            assertSame(smartTestDataSource, transactionManager.getDataSource());
            assertSame(smartTestDataSource, jdbcTemplate.getDataSource());
        } finally {
            context.close();
        }
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

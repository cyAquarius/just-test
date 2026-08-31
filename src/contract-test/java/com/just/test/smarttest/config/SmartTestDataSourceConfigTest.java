package com.just.test.smarttest.config;

import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.context.CaseExecutionContext;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SmartTestDataSourceConfigTest {

    @Test
    void defaultCaseUrlDoesNotKeepMemoryDatabaseAlive() {
        assertFalse(SmartTestDataSourceConfig.DEFAULT_H2_URL.contains("DB_CLOSE_DELAY=-1"));
    }

    @Test
    void registersFallbackRefreshScopeWhenMissing() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                SmartTestDataSourceConfig.class, RefreshScopedBeanConfig.class);
        try {
            CaseExecutionContext.bind(new CaseContext("refresh-scope", "test/refresh-scope"));
            try {
                assertNotNull(context.getBean(RefreshScopedBean.class));
            } finally {
                CaseExecutionContext.clear();
            }
        } finally {
            context.close();
        }
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
    static class RefreshScopedBeanConfig {
        @Bean
        @Scope("refresh")
        RefreshScopedBean refreshScopedBean() {
            return new RefreshScopedBean();
        }
    }

    static class RefreshScopedBean {
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

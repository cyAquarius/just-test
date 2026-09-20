package com.just.test.internal.config;

import com.just.test.context.CaseContext;
import com.just.test.internal.context.CaseExecutionContext;
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

class JustTestDataSourceConfigTest {

    @Test
    void defaultCaseUrlDoesNotKeepMemoryDatabaseAlive() {
        assertFalse(JustTestDataSourceConfig.DEFAULT_H2_URL.contains("DB_CLOSE_DELAY=-1"));
    }

    @Test
    void registersFallbackRefreshScopeWhenMissing() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                JustTestDataSourceConfig.class, RefreshScopedBeanConfig.class);
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
    void internalInfrastructureUsesJustTestDataSourceWhenAnotherPrimaryExists() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                JustTestDataSourceConfig.class, ProductionDataSourceConfig.class);
        try {
            DataSource justTestDataSource = context.getBean("justTestDataSource", DataSource.class);
            DataSourceTransactionManager transactionManager = context.getBean(
                    "justTestTransactionManager", DataSourceTransactionManager.class);
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);

            assertSame(justTestDataSource, transactionManager.getDataSource());
            assertSame(justTestDataSource, jdbcTemplate.getDataSource());
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

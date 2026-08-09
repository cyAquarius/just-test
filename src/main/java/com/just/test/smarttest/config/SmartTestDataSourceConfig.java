package com.just.test.smarttest.config;

import com.just.test.smarttest.datasource.SmartTestRoutingDataSource;
import com.just.test.smarttest.h2.SmartTestMyBatisInterceptorConfigurer;
import com.just.test.smarttest.scope.ThreadScope;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.CustomScopeConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

@Configuration
public class SmartTestDataSourceConfig {
    public static final String DEFAULT_H2_URL =
            "jdbc:h2:mem:{key};MODE=MySQL;DB_CLOSE_DELAY=-1;"
                    + "DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";

    @Bean
    public static CustomScopeConfigurer smartTestThreadScopeConfigurer() {
        CustomScopeConfigurer configurer = new CustomScopeConfigurer();
        configurer.addScope("thread", new ThreadScope());
        return configurer;
    }

    @Bean
    public static BeanPostProcessor smartTestMyBatisInterceptorConfigurer() {
        return new SmartTestMyBatisInterceptorConfigurer();
    }

    @Bean(name = "smartTestDataSource")
    @Primary
    public DataSource smartTestDataSource() {
        return new SmartTestRoutingDataSource(DEFAULT_H2_URL);
    }

    @Bean(name = "smartTestTransactionManager")
    @Primary
    public DataSourceTransactionManager smartTestTransactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}

package com.just.test.smarttest.internal.config;

import com.just.test.smarttest.internal.datasource.SmartTestRoutingDataSource;
import com.just.test.smarttest.internal.h2.SmartTestMyBatisInterceptorConfigurer;
import com.just.test.smarttest.internal.scope.ThreadScope;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.CustomScopeConfigurer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

/**
 * SmartTest 测试基础设施配置。
 *
 * <p>不对消费方提供兼容承诺。由内部 ContextCustomizer 注册到 Spring Test Context，
 * 因此必须保持 public。</p>
 */
@Configuration
public class SmartTestDataSourceConfig {
    public static final String DEFAULT_H2_URL =
            "jdbc:h2:mem:{key};MODE=MySQL;"
                    + "DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";

    @Bean
    public static CustomScopeConfigurer smartTestThreadScopeConfigurer() {
        CustomScopeConfigurer configurer = new CustomScopeConfigurer();
        configurer.addScope("thread", new ThreadScope());
        return configurer;
    }

    @Bean
    public static BeanFactoryPostProcessor smartTestRefreshScopeConfigurer() {
        return beanFactory -> {
            if (beanFactory.getRegisteredScope("refresh") == null) {
                beanFactory.registerScope("refresh", new ThreadScope());
            }
        };
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
    public DataSourceTransactionManager smartTestTransactionManager(
            @Qualifier("smartTestDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(@Qualifier("smartTestDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}

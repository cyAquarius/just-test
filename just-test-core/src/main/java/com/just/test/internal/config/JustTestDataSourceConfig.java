package com.just.test.internal.config;

import com.just.test.internal.datasource.JustTestRoutingDataSource;
import com.just.test.internal.h2.JustTestMyBatisInterceptorConfigurer;
import com.just.test.internal.scope.ThreadScope;
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
 * JustTest 测试基础设施配置。
 *
 * <p>不对消费方提供兼容承诺。由内部 ContextCustomizer 注册到 Spring Test Context，
 * 因此必须保持 public。</p>
 */
@Configuration
public class JustTestDataSourceConfig {
    public static final String DEFAULT_H2_URL =
            "jdbc:h2:mem:{key};MODE=MySQL;"
                    + "DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";

    @Bean
    public static CustomScopeConfigurer justTestThreadScopeConfigurer() {
        CustomScopeConfigurer configurer = new CustomScopeConfigurer();
        configurer.addScope("thread", new ThreadScope());
        return configurer;
    }

    @Bean
    public static BeanFactoryPostProcessor justTestRefreshScopeConfigurer() {
        return beanFactory -> {
            if (beanFactory.getRegisteredScope("refresh") == null) {
                beanFactory.registerScope("refresh", new ThreadScope());
            }
        };
    }

    @Bean
    public static BeanPostProcessor justTestMyBatisInterceptorConfigurer() {
        return new JustTestMyBatisInterceptorConfigurer();
    }

    @Bean(name = "justTestDataSource")
    @Primary
    public DataSource justTestDataSource() {
        return new JustTestRoutingDataSource(DEFAULT_H2_URL);
    }

    @Bean(name = "justTestTransactionManager")
    @Primary
    public DataSourceTransactionManager justTestTransactionManager(
            @Qualifier("justTestDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(@Qualifier("justTestDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}

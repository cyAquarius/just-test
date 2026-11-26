package com.just.test.demo.project.ordering.fixtures;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

@Configuration
public class ScannedPersistenceConfig {

    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
        TransactionTemplate template = new TransactionTemplate(manager);
        template.setTimeout(7);
        return template;
    }

    @Bean
    SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        SqlSessionFactory sessionFactory = factory.getObject();
        sessionFactory.getConfiguration().setMapUnderscoreToCamelCase(true);
        return sessionFactory;
    }
}

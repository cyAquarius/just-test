package com.just.test.smarttest.internal.h2;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SmartTestMyBatisInterceptorConfigurerTest {

    @Test
    void installsEachCompatibilityInterceptorOnce() {
        Configuration configuration = new Configuration();
        SqlSessionFactory factory = new DefaultSqlSessionFactory(configuration);
        SmartTestMyBatisInterceptorConfigurer configurer = new SmartTestMyBatisInterceptorConfigurer();

        assertSame(factory, configurer.postProcessAfterInitialization(factory, "sqlSessionFactory"));
        configurer.postProcessAfterInitialization(factory, "sqlSessionFactory");

        assertEquals(2, configuration.getInterceptors().size());
        assertEquals(H2MySqlIfInterceptor.class, configuration.getInterceptors().get(0).getClass());
        assertEquals(H2MySqlStringLiteralInterceptor.class,
                configuration.getInterceptors().get(1).getClass());
    }
}

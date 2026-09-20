package com.just.test.internal.h2;

import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * 自动把 JustTest 的 H2 兼容拦截器安装到应用提供的 MyBatis SqlSessionFactory。
 */
public class JustTestMyBatisInterceptorConfigurer implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof SqlSessionFactory) {
            Configuration configuration = ((SqlSessionFactory) bean).getConfiguration();
            addIfAbsent(configuration, H2MySqlIfInterceptor.class, new H2MySqlIfInterceptor());
            addIfAbsent(configuration, H2MySqlStringLiteralInterceptor.class,
                    new H2MySqlStringLiteralInterceptor());
        }
        return bean;
    }

    private static void addIfAbsent(Configuration configuration,
                                    Class<? extends Interceptor> interceptorType,
                                    Interceptor interceptor) {
        for (Interceptor existing : configuration.getInterceptors()) {
            if (interceptorType.isInstance(existing)) {
                return;
            }
        }
        configuration.addInterceptor(interceptor);
    }
}

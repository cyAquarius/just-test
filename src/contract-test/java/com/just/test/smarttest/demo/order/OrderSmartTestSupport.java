package com.just.test.smarttest.demo.order;

import com.just.test.smarttest.lifecycle.SmartTestLifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

/**
 * Shared mocks/lifecycle for order method packages. Abstract Support is not a
 * {@code @SmartTest} class and must not own case YAML.
 */
@ContextConfiguration(classes = OrderSmartTestSupport.OrderConfiguration.class)
public abstract class OrderSmartTestSupport implements SmartTestLifecycle {

    @Autowired
    protected OrderService orderService;

    @Configuration
    public static class OrderConfiguration {
        @Bean
        OrderService orderService() {
            return new OrderService();
        }
    }
}

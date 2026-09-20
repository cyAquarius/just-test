package com.just.test.demo.order;

import com.just.test.lifecycle.JustTestLifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

/**
 * Shared mocks/lifecycle for order method packages. Abstract Support is not a
 * {@code @JustTest} class and must not own case YAML.
 */
@ContextConfiguration(classes = OrderJustTestSupport.OrderConfiguration.class)
public abstract class OrderJustTestSupport implements JustTestLifecycle {

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

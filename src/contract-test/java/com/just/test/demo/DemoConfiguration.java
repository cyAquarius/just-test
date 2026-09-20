package com.just.test.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DemoConfiguration {
    @Bean
    public PricingService pricingService(JdbcTemplate jdbcTemplate, PricingClient pricingClient) {
        return new PricingService(jdbcTemplate, pricingClient);
    }
}

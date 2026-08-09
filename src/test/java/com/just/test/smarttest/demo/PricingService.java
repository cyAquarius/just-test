package com.just.test.smarttest.demo;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

public class PricingService {
    private final JdbcTemplate jdbcTemplate;
    private final PricingClient pricingClient;

    public PricingService(JdbcTemplate jdbcTemplate, PricingClient pricingClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.pricingClient = pricingClient;
    }

    public BigDecimal calculate(long productId, int quantity, String customerType) {
        BigDecimal price = jdbcTemplate.queryForObject(
                "SELECT price FROM demo_product WHERE id = ?",
                BigDecimal.class,
                productId);
        return price.multiply(BigDecimal.valueOf(quantity))
                .multiply(BigDecimal.valueOf(pricingClient.multiplier(customerType)));
    }
}

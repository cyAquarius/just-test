package com.just.test.demo.pricing;

import com.just.test.annotation.JustMock;
import com.just.test.context.CaseContext;
import com.just.test.demo.DemoConfiguration;
import com.just.test.demo.PricingClient;
import com.just.test.demo.PricingService;
import com.just.test.lifecycle.JustTestLifecycle;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Shared mocks/lifecycle for pricing method packages. Abstract Support is not a
 * {@code @JustTest} class and must not own case YAML.
 */
@ContextConfiguration(classes = DemoConfiguration.class)
public abstract class PricingJustTestSupport implements JustTestLifecycle {

    @Autowired
    protected PricingService pricingService;

    @JustMock
    protected PricingClient pricingClient;

    @BeforeEach
    void configureCaseMock(CaseContext context) {
        when(pricingClient.multiplier(anyString())).thenReturn(context.getInt("multiplier"));
    }
}

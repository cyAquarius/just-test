package com.just.test.smarttest.demo;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartMock;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.lifecycle.SmartTestLifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SmartTest
@ContextConfiguration(classes = DemoConfiguration.class)
class PricingServiceTest implements SmartTestLifecycle {
    @Autowired
    private PricingService pricingService;

    @SmartMock
    private PricingClient pricingClient;

    @ParameterizedTest
    @CaseSource
    void calculate(CaseContext context) {
        when(pricingClient.multiplier(anyString())).thenReturn(context.getInt("multiplier"));
        context.setResult(pricingService.calculate(
                context.getLong("productId"),
                context.getInt("quantity"),
                context.getString("customerType")));
    }
}

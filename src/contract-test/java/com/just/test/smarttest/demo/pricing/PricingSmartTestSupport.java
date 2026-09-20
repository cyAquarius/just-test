package com.just.test.smarttest.demo.pricing;

import com.just.test.smarttest.annotation.SmartMock;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.demo.DemoConfiguration;
import com.just.test.smarttest.demo.PricingClient;
import com.just.test.smarttest.demo.PricingService;
import com.just.test.smarttest.lifecycle.SmartTestLifecycle;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ContextConfiguration(classes = DemoConfiguration.class)
public abstract class PricingSmartTestSupport implements SmartTestLifecycle {

    @Autowired
    protected PricingService pricingService;

    @SmartMock
    protected PricingClient pricingClient;

    @BeforeEach
    void configureCaseMock(CaseContext context) {
        when(pricingClient.multiplier(anyString())).thenReturn(context.getInt("multiplier"));
    }
}

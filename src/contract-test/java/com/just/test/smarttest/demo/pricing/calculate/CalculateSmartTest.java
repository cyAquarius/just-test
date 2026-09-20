package com.just.test.smarttest.demo.pricing.calculate;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.demo.pricing.PricingSmartTestSupport;

@SmartTest
class CalculateSmartTest extends PricingSmartTestSupport {

    @CaseSource
    void calculate(CaseContext context) {
        context.setResult(pricingService.calculate(
                context.getLong("productId"),
                context.getInt("quantity"),
                context.getString("customerType")));
    }
}

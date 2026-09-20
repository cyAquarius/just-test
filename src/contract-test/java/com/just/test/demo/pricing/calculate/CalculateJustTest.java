package com.just.test.demo.pricing.calculate;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.demo.pricing.PricingJustTestSupport;

@JustTest
class CalculateJustTest extends PricingJustTestSupport {

    @CaseSource
    void calculate(CaseContext context) {
        context.setResult(pricingService.calculate(
                context.getLong("productId"),
                context.getInt("quantity"),
                context.getString("customerType")));
    }
}

package com.just.test.demo.order.create;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.demo.order.OrderJustTestSupport;

@JustTest
class CreateJustTest extends OrderJustTestSupport {

    @CaseSource
    void create(CaseContext context) {
        context.setResult(orderService.create(context.getLong("customerId")));
    }
}

package com.just.test.demo.order.cancel;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.demo.order.OrderJustTestSupport;

@JustTest
class CancelJustTest extends OrderJustTestSupport {

    @CaseSource
    void cancel(CaseContext context) {
        context.setResult(orderService.cancel(context.getLong("orderId")));
    }
}

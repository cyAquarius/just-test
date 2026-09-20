package com.just.test.smarttest.demo.order.cancel;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.demo.order.OrderSmartTestSupport;

@SmartTest
class CancelSmartTest extends OrderSmartTestSupport {

    @CaseSource
    void cancel(CaseContext context) {
        context.setResult(orderService.cancel(context.getLong("orderId")));
    }
}

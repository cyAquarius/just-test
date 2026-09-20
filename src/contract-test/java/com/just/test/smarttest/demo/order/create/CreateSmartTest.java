package com.just.test.smarttest.demo.order.create;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.demo.order.OrderSmartTestSupport;

@SmartTest
class CreateSmartTest extends OrderSmartTestSupport {

    @CaseSource
    void create(CaseContext context) {
        context.setResult(orderService.create(context.getLong("customerId")));
    }
}

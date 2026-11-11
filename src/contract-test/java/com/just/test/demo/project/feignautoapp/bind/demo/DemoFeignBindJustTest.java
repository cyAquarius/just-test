package com.just.test.demo.project.feignautoapp.bind.demo;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.demo.project.feignautoapp.bind.FeignBindProbe;
import com.just.test.demo.project.fixtures.feign.DemoFeignClient;
import com.just.test.demo.project.fixtures.feign.DemoFeignGateway;
import com.just.test.demo.project.fixtures.feign.OtherFeignGateway;
import com.just.test.lifecycle.JustTestLifecycle;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

@JustTest
public class DemoFeignBindJustTest implements JustTestLifecycle {

    private static final AtomicReference<String> LAST_STUB = new AtomicReference<String>();

    @JustMock
    private DemoFeignClient demoFeignClient;

    @Autowired
    private DemoFeignGateway demoFeignGateway;

    @Autowired
    private OtherFeignGateway otherFeignGateway;

    @Autowired
    private ApplicationContext applicationContext;

    @CaseSource
    void bindsDemoFeignClient(CaseContext context) {
        FeignBindProbe.share(applicationContext);
        assertNull(otherFeignGateway.ping());
        if (LAST_STUB.get() != null) {
            assertNull(demoFeignClient.ping());
        }
        String value = "demo-" + context.getCaseName();
        when(demoFeignClient.ping()).thenReturn(value);
        assertEquals(value, demoFeignGateway.ping());
        assertTrue(mockingDetails(demoFeignClient).isMock());
        assertFalse(AopUtils.isAopProxy(demoFeignClient));
        LAST_STUB.set(value);
    }
}

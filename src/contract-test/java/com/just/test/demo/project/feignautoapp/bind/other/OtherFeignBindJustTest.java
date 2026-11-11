package com.just.test.demo.project.feignautoapp.bind.other;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.demo.project.feignautoapp.bind.FeignBindProbe;
import com.just.test.demo.project.fixtures.feign.DemoFeignGateway;
import com.just.test.demo.project.fixtures.feign.OtherFeignClient;
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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockingDetails;

@JustTest
public class OtherFeignBindJustTest implements JustTestLifecycle {

    private static final AtomicReference<String> LAST_STUB = new AtomicReference<String>();

    @JustMock
    private OtherFeignClient otherFeignClient;

    @Autowired
    private OtherFeignGateway otherFeignGateway;

    @Autowired
    private DemoFeignGateway demoFeignGateway;

    @Autowired
    private ApplicationContext applicationContext;

    @CaseSource
    void bindsOtherFeignClient(CaseContext context) {
        FeignBindProbe.share(applicationContext);
        assertNull(demoFeignGateway.ping());
        if (LAST_STUB.get() != null) {
            assertNull(otherFeignClient.ping());
        }
        String value = "other-" + context.getCaseName();
        doReturn(value).when(otherFeignClient).ping();
        assertEquals(value, otherFeignGateway.ping());
        assertTrue(mockingDetails(otherFeignClient).isMock());
        assertFalse(AopUtils.isAopProxy(otherFeignClient));
        LAST_STUB.set(value);
    }
}
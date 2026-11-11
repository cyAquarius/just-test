package com.just.test.mock.bind.cos;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import com.just.test.mock.bind.BindContextProbe;
import com.just.test.mock.bind.BindMockConfiguration;
import com.just.test.mock.bind.BindMockConfiguration.CosReader;
import com.just.test.mock.bind.BindMockConfiguration.GlobalCos;
import com.just.test.mock.bind.BindMockConfiguration.RedisReader;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockingDetails;

@JustTest
@ContextConfiguration(classes = BindMockConfiguration.class)
public class CosSubsetBindJustTest implements JustTestLifecycle {

    private static final AtomicReference<String> LAST_STUB = new AtomicReference<String>();

    @JustMock
    private GlobalCos cos;

    @Autowired
    private CosReader cosReader;

    @Autowired
    private RedisReader redisReader;

    @Autowired
    private ApplicationContext applicationContext;

    @CaseSource
    void stubsCosOnly(CaseContext context) {
        BindContextProbe.share(applicationContext);
        assertNull(redisReader.read());
        if (LAST_STUB.get() != null) {
            assertNull(cos.read());
        }
        String value = "cos-" + context.getCaseName();
        doReturn(value).when(cos).read();
        assertEquals(value, cosReader.read());
        assertTrue(mockingDetails(cos).isMock());
        assertFalse(AopUtils.isAopProxy(cos));
        LAST_STUB.set(value);
    }
}

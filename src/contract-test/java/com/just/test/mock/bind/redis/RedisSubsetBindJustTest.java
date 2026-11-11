package com.just.test.mock.bind.redis;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import com.just.test.mock.bind.BindContextProbe;
import com.just.test.mock.bind.BindMockConfiguration;
import com.just.test.mock.bind.BindMockConfiguration.CosReader;
import com.just.test.mock.bind.BindMockConfiguration.GlobalRedis;
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
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

@JustTest
@ContextConfiguration(classes = BindMockConfiguration.class)
public class RedisSubsetBindJustTest implements JustTestLifecycle {

    private static final AtomicReference<String> LAST_STUB = new AtomicReference<String>();

    @JustMock
    private GlobalRedis redis;

    @Autowired
    private RedisReader redisReader;

    @Autowired
    private CosReader cosReader;

    @Autowired
    private ApplicationContext applicationContext;

    @CaseSource
    void stubsRedisOnly(CaseContext context) {
        BindContextProbe.share(applicationContext);
        assertNull(cosReader.read());
        if (LAST_STUB.get() != null) {
            assertNull(redis.read());
        }
        String value = "redis-" + context.getCaseName();
        when(redis.read()).thenReturn(value);
        assertEquals(value, redisReader.read());
        assertTrue(mockingDetails(redis).isMock());
        assertFalse(AopUtils.isAopProxy(redis));
        LAST_STUB.set(value);
    }
}

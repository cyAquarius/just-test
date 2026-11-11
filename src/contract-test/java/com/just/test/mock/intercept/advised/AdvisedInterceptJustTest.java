package com.just.test.mock.intercept.advised;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import com.just.test.mock.intercept.InterceptContextProbe;
import com.just.test.mock.intercept.fixtures.AdvisedClock;
import com.just.test.mock.intercept.fixtures.ClockReader;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockingDetails;

@JustTest
public class AdvisedInterceptJustTest implements JustTestLifecycle {

    @JustMock
    private AdvisedClock advisedClock;

    @Autowired
    private ClockReader clockReader;

    @Autowired
    private ApplicationContext applicationContext;

    @CaseSource
    void declaredAdvisedClockUsesMockitoDefaults(CaseContext context) {
        InterceptContextProbe.share(applicationContext);
        assertEquals("eastern-live", clockReader.easternNow());
        assertNull(clockReader.advisedNow());
        doReturn("stubbed").when(advisedClock).now();
        assertEquals("stubbed", clockReader.advisedNow());
        assertTrue(mockingDetails(advisedClock).isMock());
        assertFalse(AopUtils.isAopProxy(advisedClock));
    }
}

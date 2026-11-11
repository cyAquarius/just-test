package com.just.test.mock.bind.real;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import com.just.test.mock.bind.BindContextProbe;
import com.just.test.mock.bind.BindMockConfiguration;
import com.just.test.mock.bind.BindMockConfiguration.ClockReader;
import com.just.test.mock.bind.BindMockConfiguration.RealClock;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

@JustTest
@ContextConfiguration(classes = BindMockConfiguration.class)
public class RealBeanJustMockJustTest implements JustTestLifecycle {

    @JustMock
    private RealClock realClock;

    @Autowired
    private ClockReader clockReader;

    @Autowired
    private ApplicationContext applicationContext;

    @CaseSource
    void replacesRealBeanInItsOwnContext(CaseContext context) {
        BindContextProbe.distinctRealBean(applicationContext);
        assertTrue(mockingDetails(realClock).isMock());
        assertFalse(AopUtils.isAopProxy(realClock));
        when(realClock.now()).thenReturn("frozen");
        assertEquals("frozen", clockReader.now());
    }
}

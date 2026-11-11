package com.just.test.mock.intercept.eastern;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.internal.context.CaseExecutionContext;
import com.just.test.internal.scope.ThreadScope;
import com.just.test.lifecycle.JustTestLifecycle;
import com.just.test.mock.intercept.InterceptContextProbe;
import com.just.test.mock.intercept.fixtures.ClockReader;
import com.just.test.mock.intercept.fixtures.EasternClock;
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
import static org.mockito.Mockito.when;

@JustTest
public class EasternInterceptJustTest implements JustTestLifecycle {

    private static final AtomicReference<String> LAST_STUB = new AtomicReference<String>();

    @JustMock
    private EasternClock easternClock;

    @Autowired
    private ClockReader clockReader;

    @Autowired
    private ApplicationContext applicationContext;

    @CaseSource
    void stubsEasternOnly(CaseContext context) throws Exception {
        InterceptContextProbe.share(applicationContext);
        assertEquals("western-live", clockReader.westernNow());
        assertEquals("advised-raw", clockReader.advisedNow());
        assertNull(clockReader.easternNow());
        assertNull(easternClock.zone());
        if (LAST_STUB.get() != null) {
            assertNull(easternClock.now());
        }
        String value = "frozen-" + context.getCaseName();
        when(easternClock.now()).thenReturn(value);
        assertEquals(value, clockReader.easternNow());
        doReturn("direct-" + context.getCaseName()).when(easternClock).now();
        assertEquals("direct-" + context.getCaseName(), clockReader.easternNow());
        assertNull(clockReader.easternZone());
        assertOtherThreadSeesRealEastern(context);
        assertEquals("direct-" + context.getCaseName(), clockReader.easternNow());
        assertTrue(mockingDetails(easternClock).isMock());
        assertFalse(AopUtils.isAopProxy(easternClock));
        LAST_STUB.set(value);
    }

    private void assertOtherThreadSeesRealEastern(final CaseContext context) throws Exception {
        final AtomicReference<String> seen = new AtomicReference<String>();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                CaseExecutionContext.bind(context);
                try {
                    seen.set(clockReader.easternNow());
                } catch (Throwable thrown) {
                    failure.set(thrown);
                } finally {
                    ThreadScope.clearCurrentThread();
                    CaseExecutionContext.clear();
                }
            }
        });
        worker.start();
        worker.join(10000L);
        assertFalse(worker.isAlive());
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        assertEquals("eastern-live", seen.get());
    }
}

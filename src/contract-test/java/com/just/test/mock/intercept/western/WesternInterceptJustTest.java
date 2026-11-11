package com.just.test.mock.intercept.western;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import com.just.test.mock.intercept.InterceptContextProbe;
import com.just.test.mock.intercept.fixtures.ClockReader;
import com.just.test.mock.intercept.fixtures.WesternClock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@JustTest
public class WesternInterceptJustTest implements JustTestLifecycle {

    @JustMock
    private WesternClock westernClock;

    @Autowired
    private ClockReader clockReader;

    @Autowired
    private ApplicationContext applicationContext;

    @CaseSource
    void stubsWesternOnly(CaseContext context) {
        InterceptContextProbe.share(applicationContext);
        assertEquals("eastern-live", clockReader.easternNow());
        assertEquals("eastern-live", clockReader.easternZone());
        assertEquals("advised-raw", clockReader.advisedNow());
        assertNull(clockReader.westernNow());
        when(westernClock.now()).thenReturn("west-" + context.getCaseName());
        assertEquals("west-" + context.getCaseName(), clockReader.westernNow());
        assertEquals("eastern-live", clockReader.easternNow());
    }
}

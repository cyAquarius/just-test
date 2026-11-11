package com.just.test.mock.intercept.plain;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import com.just.test.mock.intercept.InterceptContextProbe;
import com.just.test.mock.intercept.fixtures.ClockReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

@JustTest
public class PlainInterceptJustTest implements JustTestLifecycle {

    @Autowired
    private ClockReader clockReader;

    @Autowired
    private ApplicationContext applicationContext;

    @CaseSource
    void realBeansStayReal(CaseContext context) {
        InterceptContextProbe.share(applicationContext);
        assertEquals("eastern-live", clockReader.easternNow());
        assertEquals("eastern-live", clockReader.easternZone());
        assertEquals("western-live", clockReader.westernNow());
        assertEquals("advised-raw", clockReader.advisedNow());
    }
}

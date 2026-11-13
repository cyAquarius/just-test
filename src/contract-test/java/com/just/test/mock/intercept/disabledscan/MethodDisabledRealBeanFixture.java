package com.just.test.mock.intercept.disabledscan;

import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustTest;
import org.junit.jupiter.api.Disabled;

@JustTest
public class MethodDisabledRealBeanFixture {

    @JustMock
    private MethodDisabledClock methodDisabledClock;

    @Disabled
    void skipped() {
    }
}

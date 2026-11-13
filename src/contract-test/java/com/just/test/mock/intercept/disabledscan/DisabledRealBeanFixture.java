package com.just.test.mock.intercept.disabledscan;

import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustTest;
import org.junit.jupiter.api.Disabled;

@JustTest
@Disabled
public class DisabledRealBeanFixture {

    @JustMock
    private DisabledClock disabledClock;
}

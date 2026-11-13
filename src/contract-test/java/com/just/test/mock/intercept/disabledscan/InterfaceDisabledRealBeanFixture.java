package com.just.test.mock.intercept.disabledscan;

import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustTest;

@JustTest
public class InterfaceDisabledRealBeanFixture implements DisabledScanApi {

    @JustMock
    private InterfaceDisabledClock interfaceDisabledClock;
}

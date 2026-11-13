package com.just.test.mock.intercept.disabledscan;

import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustTest;

@JustTest
@DisabledScanOff
public class MetaDisabledRealBeanFixture {

    @JustMock
    private MetaDisabledClock metaDisabledClock;
}

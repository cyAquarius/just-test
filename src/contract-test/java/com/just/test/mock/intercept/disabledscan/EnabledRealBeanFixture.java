package com.just.test.mock.intercept.disabledscan;

import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustTest;

/**
 * 类名故意不是 {@code *Test}，Surefire 不会执行它。扫描仍会看到 {@code @JustTest}。
 */
@JustTest
public class EnabledRealBeanFixture {

    @JustMock
    private EnabledClock enabledClock;
}

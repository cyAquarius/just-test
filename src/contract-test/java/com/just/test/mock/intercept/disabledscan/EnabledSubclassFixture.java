package com.just.test.mock.intercept.disabledscan;

import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustTest;

/**
 * 未禁用的子类。父类上的 {@code @Disabled} 没有 {@code @Inherited}，这个类仍会执行。
 */
@JustTest
public class EnabledSubclassFixture extends DisabledParentFixture {

    @JustMock
    private EnabledSubclassClock enabledSubclassClock;
}

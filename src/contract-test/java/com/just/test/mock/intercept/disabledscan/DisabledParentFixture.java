package com.just.test.mock.intercept.disabledscan;

import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustTest;
import org.junit.jupiter.api.Disabled;

/**
 * 具体的 {@code @Disabled} 父类。扫描会看到它，但它自己不能成为拦截并集的贡献类。
 */
@JustTest
@Disabled
public class DisabledParentFixture {

    @JustMock
    private DisabledParentClock disabledParentClock;
}

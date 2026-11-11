package com.just.test.mock.intercept;

import com.just.test.annotation.JustTestProject;
import com.just.test.mock.intercept.fixtures.AdvisedClock;

/**
 * 拦截契约的启动类。{@code AdvisedClock} 只出现在显式名单里，扫描到的
 * {@code @JustMock} 再并入 {@code EasternClock} / {@code WesternClock}。
 */
@JustTestProject(
        basePackages = "com.just.test.mock.intercept.fixtures",
        interceptMocks = AdvisedClock.class
)
public class JustTestApplication {
}

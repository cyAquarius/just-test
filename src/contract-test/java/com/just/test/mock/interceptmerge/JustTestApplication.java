package com.just.test.mock.interceptmerge;

import com.just.test.annotation.JustTestProject;
import com.just.test.mock.interceptmerge.fixtures.EastLane;

/**
 * 实现类能唯一拦截，接口同时命中两条车道，合并不安全，应在启动期失败。
 */
@JustTestProject(
        basePackages = "com.just.test.mock.interceptmerge.fixtures",
        interceptMocks = EastLane.class
)
public class JustTestApplication {
}

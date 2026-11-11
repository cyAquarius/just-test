package com.just.test.mock.interceptbean;

import com.just.test.annotation.JustTestProject;
import com.just.test.mock.interceptbean.fixtures.TaskServiceImpl;

/**
 * 同一 Bean 的接口 / 实现拦截。{@code interceptMocks} 先放实现类，
 * 扫描再并入接口，复现原先会包两层的安装顺序。
 */
@JustTestProject(
        basePackages = "com.just.test.mock.interceptbean.fixtures",
        interceptMocks = TaskServiceImpl.class
)
public class JustTestApplication {
}

package com.just.test.demo.project.feignoffapp;

import com.just.test.annotation.JustTestProject;
import com.just.test.demo.project.fixtures.feign.DemoFeignGateway;

/** 默认不自动 mock Feign，也不强制关闭 OkHttp。 */
@JustTestProject(
        basePackages = "com.just.test.demo.project.fixtures.feign",
        excludeClasses = DemoFeignGateway.class
)
public class JustTestApplication {
}

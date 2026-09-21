package com.just.test.demo.project.feignautoapp;

import com.just.test.annotation.JustTestProject;
import com.just.test.demo.project.fixtures.feign.ExcludedFeignClient;

/** 开启 Feign 自动 mock，并对选定类型 opt-out。 */
@JustTestProject(
        basePackages = "com.just.test.demo.project.fixtures.feign",
        autoMockFeignClients = true,
        autoMockFeignClientExcludes = ExcludedFeignClient.class
)
public class JustTestApplication {
}

package com.just.test.demo.project.okhttpapp;

import com.just.test.annotation.JustTestProject;

/** 显式开启 Feign OkHttp：不向 Environment 强制写入 feign.okhttp.enabled=false。 */
@JustTestProject(
        basePackages = "com.just.test.demo.project.fixtures.scan",
        enableFeignOkHttp = true
)
public class JustTestApplication {
}

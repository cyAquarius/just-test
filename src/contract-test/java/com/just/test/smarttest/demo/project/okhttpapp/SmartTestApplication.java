package com.just.test.smarttest.demo.project.okhttpapp;

import com.just.test.smarttest.annotation.SmartTestProject;

/** 显式开启 Feign OkHttp：不向 Environment 强制写入 feign.okhttp.enabled=false。 */
@SmartTestProject(
        basePackages = "com.just.test.smarttest.demo.project.fixtures.scan",
        enableFeignOkHttp = true
)
public class SmartTestApplication {
}

package com.just.test.smarttest.demo.project.defaultapp;

import com.just.test.smarttest.annotation.SmartTestProject;

/** 推荐的薄启动类：只声明扫描根，默认 denylist 由框架提供。 */
@SmartTestProject(basePackages = "com.just.test.smarttest.demo.project.fixtures.scan")
public class SmartTestApplication {
}

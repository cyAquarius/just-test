package com.just.test.demo.project.defaultapp;

import com.just.test.annotation.JustTestProject;

/** 推荐的薄启动类：只声明扫描根，默认 denylist 由框架提供。 */
@JustTestProject(basePackages = "com.just.test.demo.project.fixtures.scan")
public class JustTestApplication {
}

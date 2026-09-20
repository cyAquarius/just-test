package com.just.test.demo.project.mybatisapp;

import com.just.test.annotation.JustTestProject;

/** 声明 mapperPackages 后由框架装配 MyBatis → JustTest H2。 */
@JustTestProject(
        basePackages = "com.just.test.demo.project.fixtures.persist",
        mapperPackages = "com.just.test.demo.project.fixtures.persist.mapper"
)
public class JustTestApplication {
}

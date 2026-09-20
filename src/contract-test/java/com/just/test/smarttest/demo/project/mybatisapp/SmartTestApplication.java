package com.just.test.smarttest.demo.project.mybatisapp;

import com.just.test.smarttest.annotation.SmartTestProject;

/** 声明 mapperPackages 后由框架装配 MyBatis → SmartTest H2。 */
@SmartTestProject(
        basePackages = "com.just.test.smarttest.demo.project.fixtures.persist",
        mapperPackages = "com.just.test.smarttest.demo.project.fixtures.persist.mapper"
)
public class SmartTestApplication {
}

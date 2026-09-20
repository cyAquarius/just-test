package com.just.test.smarttest.demo.project.aliasapp;

import com.just.test.smarttest.annotation.SmartTestProject;

/** 声明存量数据源 / 事务 Bean 名作为 SmartTest 基础设施的兼容别名。 */
@SmartTestProject(
        basePackages = "com.just.test.smarttest.demo.project.aliasapp",
        dataSourceAliases = {"masterDataSource", "occupiedDataSource"},
        transactionManagerAliases = "masterDataTransactionManager"
)
public class SmartTestApplication {
}

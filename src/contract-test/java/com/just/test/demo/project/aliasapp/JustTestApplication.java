package com.just.test.demo.project.aliasapp;

import com.just.test.annotation.JustTestProject;

/** 声明存量数据源 / 事务 Bean 名作为 JustTest 基础设施的兼容别名。 */
@JustTestProject(
        basePackages = "com.just.test.demo.project.aliasapp",
        dataSourceAliases = {"masterDataSource", "occupiedDataSource"},
        transactionManagerAliases = "masterDataTransactionManager"
)
public class JustTestApplication {
}

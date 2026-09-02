# just-test

- 默认使用中文协作，代码标识符和命令保持英文。
- 本仓库是可复用的测试工具库，不得引入特定业务项目的包、依赖、表结构或用例。
- 框架代码统一放在 `com.just.test.smarttest` 下。
- 仅用于演示的代码放在 `src/contract-test` 的 `com.just.test.smarttest.demo` 下（boot2 / boot3 的 `testSourceDirectory`）。
- SmartTest 的公开运行模型只面向 Spring Boot；`@SmartTest` 统一负责 Boot TestContext 启动，不新增非 Boot 模式，也不要求消费测试重复声明 Boot bootstrap 注解。
- Boot 2 与 Boot 3 均为一等产品线，并由 CI 独立验证：`just-test-boot2` = Java 8 + Spring Boot 2.7.18；`just-test-boot3` = Java 17 + Spring Boot 3.5.16。
- 父 reactor 模块为 `just-test-core`、`just-test-boot2`、`just-test-boot3`；消费烟测在 `smoke-tests/boot2` 与 `smoke-tests/boot3`。共享语义放 `just-test-core`，产品线差异放对应 boot 模块，不要假设仓库只有单一 Boot 2 模块。
- 修改 Boot 2 线必须保持 Java 8 兼容；修改 Boot 3 线不得破坏 Boot 2 线，反之亦然。共享 `just-test-core` 的改动需同时考虑两条产品线。
- Java 构建和测试直接使用 Maven。

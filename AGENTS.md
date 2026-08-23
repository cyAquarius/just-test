# just-test

- 默认使用中文协作，代码标识符和命令保持英文。
- 本仓库是可复用的测试工具库，不得引入特定业务项目的包、依赖、表结构或用例。
- 框架代码统一放在 `com.just.test.smarttest` 下。
- 仅用于演示的代码放在 `src/test` 的 `com.just.test.smarttest.demo` 下。
- SmartTest 的公开运行模型只面向 Spring Boot；`@SmartTest` 统一负责 Boot TestContext 启动，不新增非 Boot 模式，也不要求消费测试重复声明 Boot bootstrap 注解。
- 产品兼容目标包括 Java 8 + Spring Boot 2.x 和 Java 17 + Spring Boot 3.x；当前模块与 CI 只验证 Java 8 + Spring Boot 2.7.18，在建立独立构建和回归验证前不得声称已支持 Boot 3.x。
- 修改当前模块必须保持 Java 8 兼容性；实现 Boot 3.x 支持时不得破坏 Java 8 + Boot 2.x 支持线。
- Java 构建和测试直接使用 Maven。

# just-test

[English](README.md)

`just-test` 是可复用的 Java 测试工具库。当前模块 SmartTest 基于 JUnit 5、Spring 与隔离的 H2 内存库，提供 YAML 驱动的集成测试能力。

它定位为测试范围框架：负责可重复的测试准备、断言与替身，不替代业务架构、生产数据库兼容性验证或业务代码的并发治理。

## 兼容性

- Java 8
- Spring Boot 2.7.18
- JUnit 5.9.3
- H2 2.x
- Maven

## SmartTest 提供的能力

- `@SmartTest` 配置 Spring Test、H2、`JdbcTemplate` 与事务管理器，使被测服务可以按正常事务行为执行。
- `@CaseSource` 发现 YAML 用例，并将 `CaseContext` 传给 JUnit 参数化测试。
- 通过 `prepare.yaml`、`response.yaml`、`expect.yaml`、`expect_exception.yaml` 完成数据准备以及结果、数据库和异常验证。
- `@SmartMock` 创建线程作用域 Mockito mock；同类型多 Bean 时，显式 `name` 优先，否则先按 Spring autowire/qualifier 规则过滤，再按 `@Primary`、字段名或 alias、唯一候选确定目标。
- `@ThreadScopedMock` 将同一 scoped mock 模型用于标注的 `@Bean` 方法。
- H2 数据库按 SmartTest `ApplicationContext` 与活动 case 隔离；case 结束时释放对应数据库，schema 初始化失败可重试。
- MyBatis 测试 SQL 进行受控的 MySQL→H2 改写：`IF(...)` 改为 `CASEWHEN(...)`；历史双引号字符串仅在已知字符串函数参数和比较运算符右值中兼容。

## 边界与并发

SmartTest 只隔离自己拥有的测试资源，不能让任意业务代码天然全局并发安全。

- 业务代码初始化的 static registry 仍由业务侧负责。
- 手工线程、`CompletableFuture` common pool 和未由 SmartTest 接管的 executor，不会自动获得 mock 或数据库上下文；没有活动 case 的数据库访问会立即失败，而不会静默创建空 H2。
- 测试方法上的 Spring `@Transactional` 与 `@Sql` 不支持用于 `@CaseSource`：它们的生命周期早于 case 绑定。请使用 `prepare.yaml` 和 `expect.yaml` 管理确定性的 case 数据。
- 重跑成功不能证明并发安全。存在 Flake 的测试应保持串行，直到其所有权和生命周期边界清晰。
- 新 SQL 应使用标准单引号字符串；双引号改写仅是兼容历史 MySQL Mapper 的过渡能力。

## 引入依赖

只在测试范围引入：

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/cyaquarius/just-test</url>
        <snapshots><enabled>true</enabled></snapshots>
    </repository>
</repositories>

<dependency>
    <groupId>com.just.test</groupId>
    <artifactId>just-test</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

GitHub Packages 需要在消费项目的 `~/.m2/settings.xml` 中配置凭据。Token 应放在项目外，例如 `${env.GITHUB_PACKAGES_TOKEN}`。classic PAT 需要 `read:packages`；消费私有源码仓库时还需要 `repo`。

## 编写测试

消费项目必须提供 `src/test/resources/sql/schema.sql`，用于初始化 H2 表结构。

```java
@SmartTest
@ContextConfiguration(classes = YourApplication.class)
class OrderServiceTest implements SmartTestLifecycle {

    @Autowired
    private OrderService orderService;

    @SmartMock
    private PricingClient pricingClient;

    @ParameterizedTest(name = "{0}")
    @CaseSource
    void createOrder(CaseContext context) {
        context.setResult(orderService.create(context.getLong("customerId")));
    }
}
```

同类型有多个候选 Bean 时，使用 `@SmartMock(name = "beanName")` 或 `@Qualifier("beanName")`。

默认目录位于测试类包名与简单类名之下：

```text
src/test/resources/com/example/order/OrderServiceTest/
└── create-order/
    ├── request.yaml
    ├── prepare.yaml
    ├── response.yaml
    ├── expect.yaml
    └── expect_exception.yaml
```

`@CaseSource("custom-root")` 可指定测试类包下的自定义根目录。默认类名目录不存在时，仍兼容旧的包级用例目录。

## YAML 文件与 Flag

- `request.yaml`：通过 `CaseContext` 的 `getString`、`getLong`、`getInt`、`getObject`、`getList`、`getMap` 获取输入。
- `prepare.yaml`：case 执行前插入的数据。
- `response.yaml`：预期返回值。
- `expect.yaml`：预期数据库记录。
- `expect_exception.yaml`：预期异常类型及可选消息。

字段后缀 Flag 用于返回值和数据库断言：

| Flag | 含义 |
| --- | --- |
| `[C]` | 行定位键；同时启用 List 无序匹配。 |
| `[CN]` | 定位的行必须不存在。 |
| `[N]` | 跳过字段；在 `prepare.yaml` 中表示不插入该列。 |
| `[R]` | 正则匹配。 |
| `[A]` | 非空断言。 |
| `[D]` / `[D60]` | 时间容差（秒），默认 60 秒。 |
| `[J]` | JSON 结构比较。 |
| `[F]` | `prepare.yaml` 中的原始数据库函数，如 `NOW()`。 |

数据库断言建议显式提供 `[C]` 字段，避免行定位含义不明确。

## 生命周期

每个 YAML case 依次执行：

1. 绑定唯一 case 身份，初始化对应数据库 schema 并准备干净数据库；
2. 加载 `prepare.yaml`；
3. 重置、预热 scoped mock，并注入 `@SmartMock` 字段；
4. 调用匹配的 `@BeforeCase("case-name")` 及 `beforeExecute`；
5. 执行 JUnit 方法，然后调用 `afterExecute`；
6. 除非 `SmartTestLifecycle` 明确跳过，否则验证异常、返回值和数据库；
7. 释放 scoped 对象与 case 数据库；清理失败作为 suppressed exception 保留，且不掩盖原始测试失败。

## 构建与发布

```bash
mvn clean verify
mvn clean install
```

推送到 `main` 会触发 Java 8 GitHub Actions 工作流，执行 `mvn clean deploy` 并发布当前版本至 GitHub Packages。仓库只包含可复用框架代码和最小演示，不应加入具体业务包、业务表结构或业务测试。

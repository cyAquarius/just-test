# just-test

[English](README.md)

`just-test` 是可复用的 Java 测试工具库。当前模块 SmartTest 基于 JUnit 5、Spring Boot 与隔离的 H2 内存库，提供 YAML 驱动的集成测试能力。

它定位为测试范围框架：负责可重复的测试准备、断言与替身，不替代业务架构、生产数据库兼容性验证或业务代码的并发治理。

## 兼容性

- Java 8
- Spring Boot 2.7.18
- JUnit 5.9.3
- H2 2.x
- Maven

## SmartTest 提供的能力

- `@SmartTest` 配置 Spring Test、H2、`JdbcTemplate` 与事务管理器，使被测服务可以按正常事务行为执行。
- `@CaseSource` 发现 YAML 用例，并为每个 case 创建完整的 JUnit test-template invocation；测试方法和标准单测生命周期方法均可注入 `CaseContext`。
- 通过 `prepare.yaml`、`response.yaml`、`expect.yaml`、`expect_exception.yaml` 完成数据准备以及结果、数据库和异常验证。
- `@SmartMock` 创建线程作用域 Mockito mock；同类型多 Bean 时，显式 `name` 优先，否则先按 Spring autowire/qualifier 规则过滤，再按 `@Primary`、字段名或 alias、唯一候选确定目标。
- `@ThreadScopedMock` 将同一 scoped mock 模型用于标注的 `@Bean` 方法。
- `StaticMockContext` 可在每个 case 线程中替换业务静态 Context/工厂入口，并在 case 结束时自动恢复。
- H2 数据库按 SmartTest `ApplicationContext` 与活动 case 隔离；清理后的 DDL 会缓存，默认从模板数据库克隆 schema（可通过 `smarttest.schema.clone=false` 禁用）；case 结束时释放对应数据库，schema 初始化失败可重试。
- MyBatis 测试 SQL 进行受控的 MySQL→H2 改写：`IF(...)` 改为 `CASEWHEN(...)`，`DATE_FORMAT(...)` 改为 `FORMATDATETIME(...)`；`DATE_FORMAT` 改写通过 MyBatis `StatementHandler` 拦截器链执行（与 `IF(...)` → `CASEWHEN(...)` 使用相同路径），因此纯 `JdbcTemplate` SQL 若未经过该拦截器则不会改写。历史双引号字符串仅在已知字符串函数参数和比较运算符右值中兼容。

## 边界与并发

SmartTest 只隔离自己拥有的测试资源，不能让任意业务代码天然全局并发安全。

- 业务代码初始化的 static registry 仍由业务侧负责。
- case 静态 Mock 只作用于当前线程，在用户 `@BeforeEach`、测试方法和用户 `@AfterEach` 中持续生效；它不能覆盖 Spring Context refresh 或早于 case invocation 的 Spring Test listener，也不会传播到业务自行创建的异步线程。
- 检测到多个 Spring Context 时，SmartTest 会输出一次风险警告；相关 case 失败时会追加诊断日志，但不会替换原始异常。任意静态 Mock 的存在不会抑制该提示，因为框架无法判断它是否覆盖了相关入口。
- 新旧测试类可以并存；未标注 `@SmartTest` 的旧测试不会启用 SmartTest 生命周期。但如果新旧测试并行执行，并且业务代码共享 JVM static ContextHolder/工厂，SmartTest 无法保护旧测试线程；应让受影响的旧测试保持串行，或迁移其静态入口。
- 手工线程、`CompletableFuture` common pool 和未由 SmartTest 接管的 executor，不会自动获得 mock 或数据库上下文；没有活动 case 的数据库或 thread-scoped mock 访问会立即失败，而不会静默创建空 H2 或未配置的 mock。
- 测试类或 `@CaseSource` 方法上的 Spring `@Transactional` 与 `@Sql` 不受支持：它们的生命周期早于 case 绑定，框架会在执行前报错。请使用 `prepare.yaml` 和 `expect.yaml` 管理确定性的 case 数据。
- 重跑成功不能证明并发安全。存在 Flake 的测试应保持串行，直到其所有权和生命周期边界清晰。
- 新 SQL 应使用标准单引号字符串；双引号改写仅是兼容历史 MySQL Mapper 的过渡能力。

### 并行配置

并行调度由消费工程的 JUnit 配置控制，SmartTest 负责并行后的框架资源隔离。推荐先启用测试类之间并行、保持同一类中的 case 串行：

```properties
# src/test/resources/junit-platform.properties
junit.jupiter.execution.parallel.enabled=true
junit.jupiter.execution.parallel.mode.default=same_thread
junit.jupiter.execution.parallel.mode.classes.default=concurrent
```

业务 static 状态、外部共享资源和异步线程均已确认安全时，可将 `mode.default` 改为 `concurrent`，允许同一测试类中的 case 并行。正常测试类不需要声明 `@Execution`；仅在个别测试无法满足并发边界时，使用 `@Execution(ExecutionMode.SAME_THREAD)` 局部降级。

### 并行落地反模式

并行失败不一定是 SmartTest 隔离失效；先排查下游项目的替身、静态状态和资源生命周期。以下用法会把使用错误伪装成 framework flake：

1. **用 `ReflectionTestUtils` 覆盖 `@SmartMock` 代理**
   - **错误：** 在 `beforeExecute` 中使用 `ReflectionTestUtils`，把 raw Mockito mock 写入业务 Bean 字段，覆盖 `@SmartMock` 创建的 `ThreadScope` proxy。业务 Bean 往往是 singleton，这会污染 singleton 字段，让其他线程或 case 看到错误的 stub。
   - **正确：** 只 stub 测试类上的 `@SmartMock` 字段，让业务 Bean 保持 scoped proxy。静态工厂或 Context 入口使用 `StaticMockContext` / `configureStaticMocks` 按 case 配置。

2. **给 case H2 URL 加数据库保活参数**
   - **错误：** 为掩盖 `already closed`，在 case URL 上增加 `DB_CLOSE_DELAY=-1`（或类似的 keep-alive 参数）。这只是延后暴露生命周期问题，会造成内存增长、完整测试运行变慢甚至 OOM。
   - **正确：** 按 [#12（already closed）](https://github.com/cyAquarius/just-test/issues/12) 的资源边界处理：case 切换时先解绑线程上的 JDBC/MyBatis holder，再释放旧 DataSource 并重建当前 case 的 DataSource。模板数据库可为 schema clone 在内部使用 `DB_CLOSE_DELAY=-1`（见 [#16](https://github.com/cyAquarius/just-test/issues/16)），但这不意味着可以把它加到 case URL；默认 case URL 不含 `DB_CLOSE_DELAY`。

3. **期待 SmartTest 自动修复业务 static registry**
   - **错误：** 将业务 static Context、factory 或 registry 的并发污染归咎于 SmartTest，期待框架自动修复，或默认在上游加入全局 `ContextCreationLock`。SmartTest 不负责这些业务静态入口的所有权和并发治理。
   - **正确：** 按落地清单排查：
     - 出现多个 Spring Context 警告时，检查 `StaticMockContext` / `configureStaticMocks` 是否覆盖相关 static Context 或 factory 入口。
     - Mapper `#0` 双 Bean 等 Bean 装配问题按名称、限定符和启动配置排查，参考 [#11](https://github.com/cyAquarius/just-test/issues/11)，不要用全局锁掩盖。
     - 无法隔离的测试保持串行：使用 `@Execution(ExecutionMode.SAME_THREAD)`，或将测试类设为串行。
     - 不要默认在上游加入全局 `ContextCreationLock`；先修正静态入口的隔离方式，无法隔离的测试就保持串行。

4. **未 stub 的外部依赖配合业务 fail-open**
   - **错误：** 关键外部依赖未 stub，业务又把异常吞掉或将异常当作成功；并行时不同返回值和时序会让它看起来像 framework flake。
   - **正确：** 在 `beforeExecute` / `@BeforeCase` 中为关键外部协作者设置明确 stub，并显式断言成功与异常路径；不要把未定义的外部返回值交给业务 fail-open 逻辑。

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

该 artifact 会传递提供 `@SmartTest` 启动和下方推荐配置所需的 Spring Boot TestContext 与 auto-configuration API；消费工程仍负责选择并管理自身的 Spring Boot 2.x 版本。

## 编写测试

SmartTest 统一使用 Spring Boot TestContext。消费项目必须提供 `src/test/resources/sql/schema.sql`，用于初始化 H2 表结构，并提供专用测试启动配置：

```java
// src/test/java/com/example/smarttest/SmartTestApplication.java
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
    basePackages = "com.example",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = Application.class
    )
)
public class SmartTestApplication {
}
```

`SmartTestApplication` 是消费工程专用于 SmartTest 的测试启动配置，不需要 `main` 方法。将它放入独立测试包，并将所有 SmartTest 测试类放在该包或其子包下，例如 `com.example.smarttest.order`。Spring Boot 会先发现这个更近的测试配置，不会继续搜索父包中的生产 `Application`；仅放在 `src/test` 并不能避免两个启动类冲突，因为测试 classpath 同时包含生产类和测试类。扫描业务根包时还应像示例一样排除生产启动类，避免其配置被组件扫描重新加载；业务组件扫描及其他排除规则、Mapper 装配和项目级外部依赖 Mock 均由消费工程在这里定义。

SmartTest Context 使用框架提供的 H2 `DataSource`、事务管理器和 `JdbcTemplate`。测试启动配置不得同时加载生产 `DataSource`、事务管理器或其他数据库基础设施配置；请通过 `test` profile 或组件扫描排除这些生产配置。框架内部会精确连接自身 H2，但不会改写用户 Bean 的 `@Primary` 属性，也不会替测试选择生产与测试数据源。

```java
@SmartTest
class OrderServiceSmartTest implements SmartTestLifecycle {

    @Autowired
    private OrderService orderService;

    @SmartMock
    private PricingClient pricingClient;

    @CaseSource
    void createOrder(CaseContext context) {
        context.setResult(orderService.create(context.getLong("customerId")));
    }
}
```

`@SmartTest` 已包含 Spring Boot bootstrapper，并通过 `test` profile 加载 `application-test.yml`，不要再组合 `@SpringBootTest` 或重复声明 `@BootstrapWith`。独立测试包内应只有一个可发现的 `@SpringBootConfiguration`。测试类不在其子包下、存在多个候选启动配置或某个测试需要特殊配置时，再使用 `@ContextConfiguration(classes = SmartTestApplication.class)` 显式选择。

同类型有多个候选 Bean 时，使用 `@SmartMock(name = "beanName")` 或 `@Qualifier("beanName")`。替换 Spring Bean 时优先使用 `@SmartMock`，不要求使用 `@MockBean`。
不要使用 `ReflectionTestUtils` 将 raw Mockito mock 写入业务 Bean 字段来替换 `@SmartMock` 的 `ThreadScope` proxy；请只 stub 测试类中的 `@SmartMock` 字段。

业务通过静态入口获取 Spring Context 时，可按 case 显式绑定：

```java
@Override
public void configureStaticMocks(CaseContext context, StaticMockContext mocks) {
    MockedStatic<SpringContextHolder> holder = mocks.mockStatic(SpringContextHolder.class);
    holder.when(SpringContextHolder::getApplicationContext)
          .thenReturn(mocks.getApplicationContext());
}
```

未 stub 的静态方法继续调用真实实现。不要手工关闭返回的 `MockedStatic`；SmartTest 会在用户 `@AfterEach` 结束后，于原 case 线程统一关闭。

该能力要求消费工程显式启用 Mockito inline mock maker；`@SmartMock` 的实际目标 Bean 为 final 类时也有相同要求。例如在消费工程的 `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` 写入 `mock-maker-inline`，或引入与 Mockito 版本一致的 `mockito-inline`。SmartTest 不会在发布的 JAR 中全局指定 MockMaker，避免覆盖消费工程已有的 Mockito/PowerMock 配置。

默认目录位于测试类包名与简单类名之下：

```text
src/test/resources/com/example/smarttest/order/OrderServiceSmartTest/
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

字段后缀 Flag 按文件阶段生效：`[C]` 用于 `expect.yaml` 的行定位和 `response.yaml` 的 List 无序匹配。只要 List 中使用了 `[C]`，每个预期元素都必须是对象并至少声明一个 `[C]` 字段；框架会拒绝混合有定位键和无定位键的无序预期。`[CN]` 仅用于 `expect.yaml`，`[F]` 仅用于 `prepare.yaml`，其他 Flag 按下表用于相应的数据准备或断言：

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

`@SmartTest` 是测试类级执行契约：类内所有可执行测试方法都必须使用 `@CaseSource`。如果类中存在 `@Test`、`@RepeatedTest`、`@ParameterizedTest`、`@TestFactory` 或其他普通 JUnit 测试方法，SmartTest 会在执行前报错并提示拆分类。渐进迁移时，保留原有 JUnit 测试类不变，将新 case 放入独立的 `@SmartTest` 类；未标注 `@SmartTest` 的旧测试类不受影响。

`@CaseSource` 本身就是测试注解，不要再与其他 JUnit 测试注解组合。每个 YAML case 依次执行：

1. 创建独立的 JUnit invocation 及其 `CaseContext`；
2. 绑定 case，创建并初始化全新的 case 数据库，然后加载 `prepare.yaml`；
3. 重置、预热 scoped mock，注入 `@SmartMock` 字段并配置 case 静态 Mock；
4. 执行用户的 `@BeforeEach`；
5. 调用匹配的 `@BeforeCase("case-name")` 与 `beforeExecute`，执行测试方法、`afterExecute` 及异常、返回值和数据库验证；
6. 执行用户的 `@AfterEach`；
7. 关闭静态与 scoped mock，释放 case 数据库；清理失败不会掩盖原始测试失败。

## 构建与发布

```bash
mvn clean verify
mvn clean install
```

面向 `main` 的 Pull Request 会先运行 Java 8 `mvn clean verify`。推送到 `main` 后，发布工作流执行 `mvn clean deploy` 并将当前版本发布至 GitHub Packages。仓库只包含可复用框架代码和最小演示，不应加入具体业务包、业务表结构或业务测试。

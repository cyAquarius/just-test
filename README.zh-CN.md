# just-test

[English](README.md)

`just-test` 是可复用的 Java 测试工具库，采用 [Apache License 2.0](LICENSE)。JustTest 通过两个独立产品线为 Spring Boot 2 和 Spring Boot 3 提供相同包名与核心语义的 YAML 驱动集成测试能力。

它定位为测试范围框架：负责可重复的测试准备、断言与替身，不替代业务架构、生产数据库兼容性验证或业务代码的并发治理。

## 兼容性与版本矩阵

| 组件 | `just-test-boot2` | `just-test-boot3` |
| --- | --- | --- |
| Java | 8 | 17 |
| Spring Boot | 2.7.18 | 3.5.16 |
| Spring Framework | 5.3.31 | 6.2.19 |
| JUnit Jupiter | 5.14.4 | 5.12.2 |
| Mockito | 4.11.0 | 5.17.0 |
| MyBatis / mybatis-spring | 3.5.19 / 2.1.2 | 3.5.19 / 3.0.6 |
| H2 | 2.1.214 | 2.3.232 |
| SnakeYAML | 1.33 | 2.4 |
| fastjson2 | 2.0.64 | 2.0.64 |
| SLF4J | 1.7.36 | 2.0.18 |

Boot 2 线保留最后一个正式版本 2.7.18 和真实 Java 8 基线；Boot 3 线使用仍属 Boot 3、基于 Java 17 的稳定版本 3.5.16，不升级到 Boot 4。除 MyBatis、mybatis-spring、fastjson2、Boot 2 的 Mockito/SnakeYAML、Boot 2 的 `junit-bom` 覆盖（JUnit Jupiter 5.14.4）和构建插件外，版本由对应 Spring Boot BOM 管理。构建使用 Maven Compiler Plugin 3.15.0 和 Surefire 3.5.6；CI 使用 `actions/checkout@v7`、`actions/setup-java@v6`。

上表是两条产品线独立构建和验证时的基线。CI 还会在匹配的 Spring Boot parent/BOM 下跑消费烟测，覆盖常见业务工程结构。

## JustTest 提供的能力

- `@JustTest` 配置 Spring Test、H2、`JdbcTemplate` 与事务管理器，使被测服务可以按正常事务行为执行；不存在 `refresh` scope 时会注册一个替代实现，使使用 `@RefreshScope` 或 `@Scope("refresh")` 的业务 Bean 无需 Spring Cloud refresh 基础设施即可加载，但测试不提供真实的 refresh 语义。
- `@JustTestProject` 是推荐的消费方启动注解：空类加上 `basePackages`（以及可选的 `mapperPackages`）即可组合 `@SpringBootConfiguration`、`@EnableAutoConfiguration`、默认组件扫描排除、可选的 MyBatis→JustTest 数据源装配，以及 `dataSource` / `transactionManager` 别名（还可用 `dataSourceAliases` / `transactionManagerAliases` 声明存量 Bean 名）。传输层 / OkHttp 由消费工程自行选择，框架不会写入 `feign.okhttp.enabled=false` / `spring.cloud.openfeign.okhttp.enabled=false`；`FeignAutoConfiguration` / `FeignContext` 仍保留。需要时设 `autoMockFeignClients = true`，只为带 `@FeignClient` 的接口注册与 `@JustMock` 相同的线程作用域 Mockito mock（不按 `*Client` 名字推断，也不自动 mock Redis / OSS / SDK）。显式 `@JustMock` / `@ThreadScopedMock` 优先；`autoMockFeignClientExcludes` 可对选定 Feign 类型 opt-out。已有手写启动类可继续使用。
- `@CaseSource` 发现 YAML 用例，并为每个 case 创建完整的 JUnit test-template invocation；测试方法和标准单测生命周期方法均可注入 `CaseContext`。
- 通过 `prepare.yaml`、`response.yaml`、`expect.yaml`、`expect_exception.yaml` 完成数据准备以及结果、数据库和异常验证。
- `@JustMock` 创建线程作用域 Mockito mock；同类型多 Bean 时，显式 `name` 只命中类型兼容的 Bean；如果现有 Bean 类型无法解析（例如 `FactoryBean` 隐藏了对象类型），仍会使用名称回退。名称相同但类型无关的 Bean 不会被替换，错误的显式名称仍会以缺少候选 Bean 的错误失败。
- `@ThreadScopedMock` 将同一 scoped mock 模型用于标注的 `@Bean` 方法。
- `StaticMockContext` 可在每个 case 线程中替换业务静态 Context/工厂入口，并在 case 结束时自动恢复。
- H2 数据库按 JustTest `ApplicationContext` 与活动 case 隔离；清理后的 DDL 会缓存，默认从模板数据库克隆 schema（可通过 `justtest.schema.clone=false` 禁用）；case 结束时释放对应数据库，schema 初始化失败可重试。对于 MySQL 的 `schema.sql` dump，清理器会剥离常见的 `SHOW CREATE TABLE` 附加语法，例如表级 `ROW_FORMAT`、`UNSIGNED`、`ON UPDATE CURRENT_TIMESTAMP`、列级 `CHARACTER SET` 和 `DEFAULT b'0'`；不宣称覆盖所有 MySQL 方言。
- MyBatis 测试 SQL 进行受控的 MySQL→H2 改写：`IF(...)` 改为 `CASEWHEN(...)`，`DATE_FORMAT(...)` 改为 `FORMATDATETIME(...)`；`DATE_FORMAT` 改写通过 MyBatis `StatementHandler` 拦截器链执行（与 `IF(...)` → `CASEWHEN(...)` 使用相同路径），因此纯 `JdbcTemplate` SQL 若未经过该拦截器则不会改写。历史双引号字符串仅在已知字符串函数参数和比较运算符右值中兼容。

## 边界与并发

JustTest 只隔离自己拥有的测试资源，不能让任意业务代码天然全局并发安全。

- 业务代码初始化的 static registry 仍由业务侧负责。
- case 静态 Mock 只作用于当前线程，在用户 `@BeforeEach`、测试方法和用户 `@AfterEach` 中持续生效；它不能覆盖 Spring Context refresh 或早于 case invocation 的 Spring Test listener，也不会传播到业务自行创建的异步线程。
- 检测到多个 Spring Context 时，JustTest 会输出一次风险警告；相关 case 失败时会追加诊断日志，但不会替换原始异常。任意静态 Mock 的存在不会抑制该提示，因为框架无法判断它是否覆盖了相关入口。
- 新旧测试类可以并存；未标注 `@JustTest` 的旧测试不会启用 JustTest 生命周期。但如果新旧测试并行执行，并且业务代码共享 JVM static ContextHolder/工厂，JustTest 无法保护旧测试线程；应让受影响的旧测试保持串行，或迁移其静态入口。
- 手工线程、`CompletableFuture` common pool 和未由 JustTest 接管的 executor，不会自动获得 mock 或数据库上下文；没有活动 case 的数据库或 thread-scoped mock 访问会立即失败，而不会静默创建空 H2 或未配置的 mock。`@JustTest` 拥有 H2 基础设施时，若框架 `JdbcTemplate` 或 `JustTestRoutingDataSource` 缺失，同样视为配置错误并立即失败，而不会跳过 prepare/verify。
- 测试类或 `@CaseSource` 方法上的 Spring `@Transactional` 与 `@Sql` 不受支持：它们的生命周期早于 case 绑定，框架会在执行前报错。请使用 `prepare.yaml` 和 `expect.yaml` 管理确定性的 case 数据。
- 重跑成功不能证明并发安全。存在 Flake 的测试应保持串行，直到其所有权和生命周期边界清晰。
- 新 SQL 应使用标准单引号字符串；双引号改写仅是兼容历史 MySQL Mapper 的过渡能力。

### 并行配置

并行调度由消费工程的 JUnit 配置控制，JustTest 负责并行后的框架资源隔离。推荐先启用测试类之间并行、保持同一类中的 case 串行：

```properties
# src/test/resources/junit-platform.properties
junit.jupiter.execution.parallel.enabled=true
junit.jupiter.execution.parallel.mode.default=same_thread
junit.jupiter.execution.parallel.mode.classes.default=concurrent
```

业务 static 状态、外部共享资源和异步线程均已确认安全时，可将 `mode.default` 改为 `concurrent`，允许同一测试类中的 case 并行。正常测试类不需要声明 `@Execution`；仅在个别测试无法满足并发边界时，使用 `@Execution(ExecutionMode.SAME_THREAD)` 局部降级。

`@TestInstance(PER_CLASS)` 不能与类内并发 case 组合——仅当 JUnit 并行已启用（`junit.jupiter.execution.parallel.enabled=true`），且 case 会并发执行（`@Execution(CONCURRENT)` 或 `mode.default=concurrent`）时：共享测试实例会竞态 `@JustMock` 字段注入。若并行未启用，残留的 `@Execution` / `mode.default=concurrent` 不触发该检查。并发 case 请使用 JUnit 默认的 `PER_METHOD`；需要 `PER_CLASS` 时保持 `SAME_THREAD`。类间并行（`mode.classes.default`）不等于类内 case 并发。

### 并行落地反模式

并行失败不一定是 JustTest 隔离失效；先排查下游项目的替身、静态状态和资源生命周期。以下用法会把使用错误伪装成 framework flake：

1. **用 `ReflectionTestUtils` 覆盖 `@JustMock` 代理**
   - **错误：** 在 `beforeExecute` 中使用 `ReflectionTestUtils`，把 raw Mockito mock 写入业务 Bean 字段，覆盖 `@JustMock` 创建的 `ThreadScope` proxy。业务 Bean 往往是 singleton，这会污染 singleton 字段，让其他线程或 case 看到错误的 stub。
   - **正确：** 只 stub 测试类上的 `@JustMock` 字段，让业务 Bean 保持 scoped proxy。静态工厂或 Context 入口使用 `StaticMockContext` / `configureStaticMocks` 按 case 配置。

2. **给 case H2 URL 加数据库保活参数**
   - **错误：** 为掩盖 `already closed`，在 case URL 上增加 `DB_CLOSE_DELAY=-1`（或类似的 keep-alive 参数）。这只是延后暴露生命周期问题，会造成内存增长、完整测试运行变慢甚至 OOM。
   - **正确：** 按 [#12（already closed）](https://github.com/cyAquarius/just-test/issues/12) 的资源边界处理：case 切换时先解绑线程上的 JDBC/MyBatis holder，再释放旧 DataSource 并重建当前 case 的 DataSource。模板数据库可为 schema clone 在内部使用 `DB_CLOSE_DELAY=-1`（见 [#16](https://github.com/cyAquarius/just-test/issues/16)），但这不意味着可以把它加到 case URL；默认 case URL 不含 `DB_CLOSE_DELAY`。

3. **期待 JustTest 自动修复业务 static registry**
   - **错误：** 将业务 static Context、factory 或 registry 的并发污染归咎于 JustTest，期待框架自动修复，或默认在上游加入全局 `ContextCreationLock`。JustTest 不负责这些业务静态入口的所有权和并发治理。
   - **正确：** 按落地清单排查：
     - 出现多个 Spring Context 警告时，检查 `StaticMockContext` / `configureStaticMocks` 是否覆盖相关 static Context 或 factory 入口。
     - Mapper `#0` 双 Bean 等 Bean 装配问题按名称、限定符和启动配置排查，参考 [#11](https://github.com/cyAquarius/just-test/issues/11)，不要用全局锁掩盖。
     - 无法隔离的测试保持串行：使用 `@Execution(ExecutionMode.SAME_THREAD)`，或将测试类设为串行。
     - 不要默认在上游加入全局 `ContextCreationLock`；先修正静态入口的隔离方式，无法隔离的测试就保持串行。

4. **未 stub 的外部依赖配合业务 fail-open**
   - **错误：** 关键外部依赖未 stub，业务又把异常吞掉或将异常当作成功；并行时不同返回值和时序会让它看起来像 framework flake。
   - **正确：** 在 `beforeExecute` / `@BeforeCase` 中为关键外部协作者设置明确 stub，并显式断言成功与异常路径；不要把未定义的外部返回值交给业务 fail-open 逻辑。

## Maven Central

坐标为 `io.github.cyaquarius`。Java 包名仍是 `com.just.test`。Maven Central **只发布** `just-test-boot2` 与 `just-test-boot3`（`just-test-core` 打进这两个 JAR，不单独上架）。`1.2.1` 是当前发布坐标，消费方应使用 Central 坐标。本地 `mvn install` 仍用于基于本仓库开发。

Java 8 / Boot 2：

```xml
<dependency>
    <groupId>io.github.cyaquarius</groupId>
    <artifactId>just-test-boot2</artifactId>
    <version>1.2.1</version>
    <scope>test</scope>
</dependency>
```

Java 17 / Boot 3：把 `artifactId` 换成 `just-test-boot3`。不要同时引入两个顶层 artifact。不要单独声明 `just-test-core`——它已打进 boot JAR，且没有独立的 Central 坐标。

若公司私服已代理 Central，只需声明依赖；若无法访问 Central，请代理 Central，或将 boot2/boot3 的 `1.2.1` 制品上传到私服 release 仓库，并保持坐标为 `io.github.cyaquarius`。

`1.2.1` 撤回框架默认关闭 OkHttp，并提供 opt-in 的 `@JustTestProject(autoMockFeignClients)`，仅自动 mock `@FeignClient` 接口。仍保留 `FeignAutoConfiguration` / `FeignContext`，且不整段排除 Feign 自动配置。

AI 编写用例：按仓库内配方 [docs/ai-justtest-authoring.md](docs/ai-justtest-authoring.md)。

## 公开 API

稳定公开 API 在 `com.just.test` 下：`annotation`（`@JustTest`、`@JustTestProject`、`@CaseSource`、`@JustMock`、`@BeforeCase`、`@ThreadScopedMock`）、`CaseContext`、`JustTestLifecycle` 和 `StaticMockContext`。`JustTestMarker` 与 Boot 模块的 `JustTestClassValidationExtension` 只供 `@JustTest` 注册 JUnit/Spring 基础设施，不要直接依赖。引擎类型放在 `com.just.test.internal`，即便因 JUnit / Spring 注册而保持 public，也不对消费方提供兼容承诺。迁移到 Boot 3 时，消费工程自身使用的 Java EE 类型仍需按 Spring Boot 3 规则迁移到 Jakarta。Java SE 的 `javax.sql.DataSource` 不属于 Jakarta 迁移范围。

## 编写测试

AI 编写配方（方法包布局、Java glue、Flag、反模式）见 [docs/ai-justtest-authoring.md](docs/ai-justtest-authoring.md)。

JustTest 统一使用 Spring Boot TestContext。启动知识分成三层：

1. **框架拥有：** H2、事务管理器、`JdbcTemplate`、默认组件扫描 denylist（**不**排除 `FeignAutoConfiguration` / `FeignContext`），以及（声明了 `mapperPackages` 时）绑定 JustTest DataSource 的 MyBatis `SqlSessionFactory` / `SqlSessionTemplate` / MapperScan。框架不选择 Feign HTTP 客户端，也不自动 mock Redis、对象存储、SDK 或其他非 Feign Bean。
2. **默认可覆盖：** `excludeClasses`、`excludeFilters`、`includeFilters`、`excludeAutoConfiguration`、`autoMockFeignClients`、`autoMockFeignClientExcludes`。消费方不必复制默认 denylist。`enableFeignOkHttp` 已弃用且不再写入任何属性。
3. **项目必须声明：** `basePackages`（不猜测业务根包）、`src/test/resources/sql/schema.sql`，以及非 Feign 外部依赖在 Support 上的显式 `@JustMock` / `@ThreadScopedMock`。不要把生产 DataSource / 事务管理器配置放到测试启动类上。

```java
// src/test/java/com/example/justtest/JustTestApplication.java
@JustTestProject(
    basePackages = "com.example",
    mapperPackages = "com.example.mapper", // 可选；没有 MyBatis 时省略
    dataSourceAliases = "masterDataSource", // 可选的存量 Bean 名
    transactionManagerAliases = "masterDataTransactionManager"
    // autoMockFeignClients = true // 按需：只自动 mock @FeignClient 接口
    // autoMockFeignClientExcludes = { BillingClient.class }
)
public class JustTestApplication {
}
```

`@JustTestProject` 元注解组合 `@SpringBootConfiguration` 与 `@EnableAutoConfiguration`，类体可以为空。默认扫描会排除 `basePackages` 下的其他 `@SpringBootApplication` / `@SpringBootConfiguration`，并在注解存在时排除 `@Controller` / `@RestController` / `@ControllerAdvice`；classpath 上有 OpenFeign 时排除 `@FeignClient`；能安全探测到的 Job / 调度刻板类型也会排除。OkHttp / Feign 传输层由项目自行决定：JustTest 不会写入 `feign.okhttp.enabled` 或 `spring.cloud.openfeign.okhttp.enabled`。若 `OkHttpFeignConfiguration` 注册了名为 `client` 的 Bean，与 `@Resource private XxxClient client` 冲突，请在项目中关闭 OkHttp，或重命名 / 使用 `@Resource(name = ...)`。`FeignAutoConfiguration` 与 `FeignContext` 仍会装配。`autoMockFeignClients` 默认为 `false`；设为 `true` 时只发现带 `@FeignClient` 的接口（按注解存在，不按 `*Client` 名字），并注册与 `@JustMock` 相同的线程作用域 Mockito mock。同一类型上显式 `@JustMock` / `@ThreadScopedMock` 优先于自动 mock；`autoMockFeignClientExcludes` 可跳过选定 Feign 类型。Redis、OSS、SDK 等仍需在 Support / MockConfig 上显式 mock。`dataSource` 与 `transactionManager` 在名称未被占用时注册为 JustTest 主 Bean 的别名。`dataSourceAliases` / `transactionManagerAliases` 按同样规则为存量 `@Qualifier` / `@Transactional` 注册额外别名：空白项忽略、重复项去重，目标名已有 Bean 定义或别名时不覆盖（会打出跳过诊断日志）。声明了 `mapperPackages` 但缺少 mybatis-spring 会 fail-fast；有 MyBatis 但未声明 `mapperPackages` 时不会猜测扫描根。

手写 `@SpringBootConfiguration` + `@EnableAutoConfiguration` + `@ComponentScan` 启动类仍然有效。新项目请优先使用 `@JustTestProject`。

`JustTestApplication` 是消费工程专用于 JustTest 的测试启动配置，不需要 `main` 方法。将它放入独立测试包，并将所有 JustTest 测试类放在该包或其子包下，例如 `com.example.justtest.order`。Spring Boot 会先发现这个更近的测试配置，不会继续搜索父包中的生产 `Application`；仅放在 `src/test` 并不能避免两个启动类冲突，因为测试 classpath 同时包含生产类和测试类。非 JustTest 的普通 JUnit 测试放在该树之外。用例目录仍是 Support + 方法包，不要另造一套 case 根约定。

JustTest Context 使用框架提供的 H2 `DataSource`、事务管理器和 `JdbcTemplate`。其余生产持久化配置请用 `test` profile 或 `excludeClasses` / `excludeFilters` 排除。框架不会改写用户 Bean 的 `@Primary` 属性，也不会替测试选择生产与测试数据源。非 Feign 业务 Client 不会自动 mock；Feign 自动 mock 需显式打开 `autoMockFeignClients`。

```java
// src/test/java/com/example/justtest/order/OrderJustTestSupport.java
abstract class OrderJustTestSupport implements JustTestLifecycle {

    @Autowired
    protected OrderService orderService;

    @JustMock
    protected PricingClient pricingClient;
}

// src/test/java/com/example/justtest/order/create/CreateJustTest.java
@JustTest
class CreateJustTest extends OrderJustTestSupport {

    @CaseSource
    void createOrder(CaseContext context) {
        context.setResult(orderService.create(context.getLong("customerId")));
    }
}
```

框架不会从 `@CaseSource` 方法的返回值采集结果（方法本身是 `void`）。要用 `response.yaml` 或 `verifyResult` 时必须调用 `context.setResult(...)`；漏写则按 `null` 断言。

`@JustTest` 已包含 Spring Boot bootstrapper，并通过 `test` profile 加载 `application-test.yml`，不要再组合 `@SpringBootTest` 或重复声明 `@BootstrapWith`。独立测试包内应只有一个可发现的 `@SpringBootConfiguration`。测试类不在其子包下、存在多个候选启动配置或某个测试需要特殊配置时，再使用 `@ContextConfiguration(classes = JustTestApplication.class)` 显式选择。

同类型有多个候选 Bean 时，使用 `@JustMock(name = "beanName")` 或 `@Qualifier("beanName")`。替换 Spring Bean 时优先使用 `@JustMock`，不要求使用 `@MockBean`。
不要使用 `ReflectionTestUtils` 将 raw Mockito mock 写入业务 Bean 字段来替换 `@JustMock` 的 `ThreadScope` proxy；请只 stub 测试类中的 `@JustMock` 字段。

业务通过静态入口获取 Spring Context 时，可按 case 显式绑定：

```java
@Override
public void configureStaticMocks(CaseContext context, StaticMockContext mocks) {
    MockedStatic<SpringContextHolder> holder = mocks.mockStatic(SpringContextHolder.class);
    holder.when(SpringContextHolder::getApplicationContext)
          .thenReturn(mocks.getApplicationContext());
}
```

未 stub 的静态方法继续调用真实实现。不要手工关闭返回的 `MockedStatic`；JustTest 会在用户 `@AfterEach` 结束后，于原 case 线程统一关闭。

Boot 2 使用 Mockito 4，静态 Mock 或 final 类型 Mock 要求消费工程显式启用 inline mock maker，例如在 `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` 写入 `mock-maker-inline`，或引入版本一致的 `mockito-inline`。Boot 3 使用 Mockito 5，其默认 mock maker 已是 inline；若消费工程覆盖了 MockMaker，仍需自行保证静态/final Mock 能力。JustTest 不会全局指定 MockMaker，避免覆盖消费工程已有配置。

默认 case 根是测试类所在包。case 目录是 classpath 上 `.java` 的同级兄弟（`{packagePath}/*/*.yaml|yml`）。框架不会探测 `{packagePath}/{SimpleClassName}/`。同一包内最多一个**自身直接标注** `@JustTest` 的具体类；仅从父类继承 marker 不能准入。抽象 Support 基类放在父级业务类目录，只放共享生命周期和 mock，不要标 `@JustTest`，也不要放 case YAML；抽象类上标 `@JustTest` 会 fail-fast。一个具体测试类只放一个 `@CaseSource` 方法——同一类上多个 `@CaseSource` 会共享本包全部 sibling case。

推荐树（与测试类一起放在 `src/test/java`）：

```text
src/test/java/com/example/justtest/order/
├── OrderJustTestSupport.java
├── create/
│   ├── CreateJustTest.java
│   ├── ok/
│   ├── dup/
│   └── bad-input/
└── cancel/
    ├── CancelJustTest.java
    └── ok/
```

每个 case 目录按需要放 `request.yaml`、`prepare.yaml`、`response.yaml`、`expect.yaml`、`expect_exception.yaml`。

把这些 YAML 映射进测试 classpath：

```xml
<testResources>
    <testResource>
        <directory>src/test/java</directory>
        <includes>
            <include>**/*.yaml</include>
            <include>**/*.yml</include>
        </includes>
    </testResource>
    <testResource>
        <directory>src/test/resources</directory>
    </testResource>
</testResources>
```

`src/test/resources` 留给 `schema.sql` 和 `application-test.yml`。YAML 也可以按同一包路径放在 `src/test/resources`；发现路径不变。

`@CaseSource("custom-root")` 是显式逃生口，解析为 `{packagePath}/{custom-root}/`。本包根或自定义根缺失时直接以 `No YAML case directories found` 失败，并说明期望的 sibling 布局；不会静默回退到类名目录或其他根。

## YAML 文件与 Flag

- `request.yaml`：通过 `CaseContext` 的 `getString`、`getLong`、`getInt`、`getObject`、`getList`、`getMap` 获取输入。
- `prepare.yaml`：case 执行前插入的数据。
- `response.yaml`：预期返回值。只与 `CaseContext.setResult` 写入的对象比较，不会读取测试方法的 Java 返回值。
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
| `[D]` / `[D60]` | 实际时间落在距*当前时刻* N 秒内（默认 60 秒）。YAML 中的期望值不参与比较。 |
| `[J]` | JSON 结构比较。 |
| `[F]` | `prepare.yaml` 中的原始数据库函数，如 `NOW()`。 |

数据库断言建议显式提供 `[C]` 字段，避免行定位含义不明确。

## 生命周期

`@JustTest` 是测试类级执行契约：测试类必须实现 `JustTestLifecycle`，类内所有可执行测试方法都必须使用 `@CaseSource`。如果类未实现该接口，或类中存在 `@Test`、`@RepeatedTest`、`@ParameterizedTest`、`@TestFactory` 或其他普通 JUnit 测试方法，JustTest 会在 `BeforeAll` 报错并提示修正。`@TestInstance(PER_CLASS)` 与类内并发 case 组合（仅当 `junit.jupiter.execution.parallel.enabled=true`）也会在 `BeforeAll` 失败。渐进迁移时，保留原有 JUnit 测试类不变，将新 case 放入独立的 `@JustTest` 类；未标注 `@JustTest` 的旧测试类不受影响。

`@CaseSource` 本身就是测试注解，不要再与其他 JUnit 测试注解组合。每个 YAML case 依次执行：

1. 创建独立的 JUnit invocation 及其 `CaseContext`；
2. 绑定 case，创建并初始化全新的 case 数据库，然后加载 `prepare.yaml`；
3. 重置、预热 scoped mock，注入 `@JustMock` 字段并配置 case 静态 Mock；
4. 执行用户的 `@BeforeEach`；
5. 调用匹配的 `@BeforeCase("case-name")` 与 `beforeExecute`，执行测试方法；无论测试方法返回还是抛出，都调用 `afterExecute`，再进行异常、返回值和数据库验证。若 `afterExecute` 自身抛出，则跳过 YAML 验证并直接抛出该异常（若测试方法也抛过异常，会作为 suppressed 附加）。无 `expect_exception.yaml` 且 `verifyException` 未处理的异常会在 `afterExecute` 与 YAML 验证之后重新抛出；
6. 执行用户的 `@AfterEach`；
7. 关闭静态与 scoped mock，释放 case 数据库；清理失败不会掩盖原始测试失败。

## 构建与验证

仅有 Java 8 的环境可独立验证 Boot 2：

```bash
JAVA_HOME=/path/to/jdk8 PATH="$JAVA_HOME/bin:$PATH" \
  mvn --batch-mode --no-transfer-progress -pl just-test-boot2 -am clean install
JAVA_HOME=/path/to/jdk8 PATH="$JAVA_HOME/bin:$PATH" \
  mvn --batch-mode --no-transfer-progress -f smoke-tests/boot2/pom.xml clean test
```

Java 17 环境可独立验证 Boot 3：

```bash
JAVA_HOME=/path/to/jdk17 PATH="$JAVA_HOME/bin:$PATH" \
  mvn --batch-mode --no-transfer-progress -pl just-test-boot3 -am clean install
JAVA_HOME=/path/to/jdk17 PATH="$JAVA_HOME/bin:$PATH" \
  mvn --batch-mode --no-transfer-progress -f smoke-tests/boot3/pom.xml clean test
```

两个产品线都执行同一份 `src/contract-test` 契约测试；各自的第二条命令跑 `smoke-tests/` 下对应工程。Java 17 下可用 `mvn clean install` 构建整个 reactor，但它不能替代上述真实 Java 8 验证。

CI 为两个 JDK 提供独立完整 Job，并跑消费烟测。仓库只包含可复用框架代码和最小演示，不应加入具体业务包、业务表结构或业务测试。

---
name: just-test
description: 在 Spring Boot 消费工程中编写或核对 JustTest 用例时使用（YAML + 薄 Java glue，测服务链路）。从需求和接口出发，不按内部方法拆单测。不要用于贡献 just-test 框架本身，也不用于普通单元测试或端到端测试。
---

# JustTest 采用方编写用例

给消费工程里的模型或代理。产品细节以仓库 [README.zh-CN.md](../../../README.zh-CN.md) / [README.md](../../../README.md) 和 [docs/ai-justtest-authoring.md](../../ai-justtest-authoring.md) 为准；本 skill 只收生成与核对规则。当前已发布版本是 `1.2.9`。

- 不要编造公司业务包、表结构或用例；只根据当前工程里已有的需求、接口和代码写。
- 不要改 just-test 框架源码，也不要依赖 `com.just.test.internal`。

待发布 `1.2.10`：`io.github.cyaquarius:just-test-boot2:1.2.10` / `io.github.cyaquarius:just-test-boot3:1.2.10` 会加载 case 目录中的 `.yml` 阶段文件；同一阶段 `.yaml` 与 `.yml` 同时存在时 fail-fast。重复 key、空的或缺少 `type` 的 `expect_exception.yaml`、以及 `[C]` 命中多行都会 fail-fast。`[J]` 按 BigDecimal 比较数值。`DATE_FORMAT` 的英文 literal 会加引号；`%M`、`%T`、`%%` 会改写；未知说明符 fail-fast，错误信息包含原始格式串。Maven Central 已发布版本仍是 `1.2.9`。消费待发布修复需先本地 `mvn install`。

## 做什么、不做什么

JustTest 测的是同一条 Spring 服务切片里的调用链：从需求或接口写出准备、输入、期望，跑入口方法，检查返回值、异常和库表。改了下层内部方法、上层契约仍按原样跑时，这里比单测更容易抓住回归。

它不是单元测试的替代品。纯函数、算法和局部边界仍用手写单测。它也不是端到端或生产库验证：外部系统用替身，库表走按 case 隔离的 H2。只能发现契约写到了的影响，写不到的层发现不了。

## 生成顺序

```mermaid
flowchart LR
  req["需求或接口"] --> method["一个业务入口方法"]
  method --> coord["只选 boot2 或 boot3"]
  coord --> app["JustTestApplication"]
  app --> tree["Support + 方法包"]
  tree --> glue["一个 @CaseSource"]
  glue --> yaml["sibling YAML"]
  yaml --> check["对照文末核对清单"]
```

1. 先锁定要回归的业务入口（服务方法或应用层接口），不要按私有方法或内部方法拆用例。
2. 按成功、重复、非法输入、外部失败等意图拆 case，不要按代码分支机械穷举。
3. 再用薄 Java glue 调用该入口，把外部依赖显式 stub 掉。
4. 断言只写在 YAML 里：`request.yaml`、`prepare.yaml`、`response.yaml`、`expect.yaml`、`expect_exception.yaml`。不必五个文件都写。

## 坐标

只选一条产品线，`scope` 用 `test`，版本用已发布的 `1.2.9`：

| 消费工程 | Maven Central |
| --- | --- |
| Java 8 / Spring Boot 2 | `io.github.cyaquarius:just-test-boot2:1.2.9` |
| Java 17 / Spring Boot 3 | `io.github.cyaquarius:just-test-boot3:1.2.9` |

- 不要同时引入两个顶层 artifact。
- 不要单独声明 `just-test-core`：它已 shade 进 boot JAR，Central 不上架。

## 启动类

消费工程必须提供 `src/test/resources/sql/schema.sql`，以及专用启动类：

```java
@JustTestProject(
    basePackages = "com.example",
    mapperPackages = "com.example.mapper", // 可选
    dataSourceAliases = "masterDataSource", // 可选存量 Bean 名
    transactionManagerAliases = "masterDataTransactionManager"
    // autoMockFeignClients = true // 按需：只自动 mock @FeignClient
    // interceptMocks = { PricingClient.class } // 包树外需要拦截的真实 Bean
)
public class JustTestApplication {
}
```

- 放入独立测试包。所有 `@JustTest` 类放在该包或其子包。
- 项目只声明 `basePackages`、差异过滤器、需要兼容的数据源 / 事务别名，以及是否打开 `autoMockFeignClients`。
- 不要再叠 `@SpringBootTest` 或 `@BootstrapWith`。`@JustTest` 已包含 Boot TestContext 与 `test` profile。
- 启动配置不得加载生产 `DataSource` / 事务管理器。
- 不默认关闭 OkHttp。`autoMockFeignClients` 默认关闭；打开后只按 `@FeignClient` 注解 mock，不按 `*Client` 名字推断。
- Redis / OSS / SDK / 自定义 Client 必须在 Support 上显式 `@JustMock`（或 `@ThreadScopedMock`）。
- 显式 `@JustMock` / `@JustSpy` / `@ThreadScopedMock` 优先于自动 Feign mock。
- JustTest **不会**自动 exclude 第三方 `*AutoConfiguration`。与同类型生产 Bean 冲突时启动期 fail-fast，不要指望静默替换。
- 手写 `@SpringBootConfiguration` 启动类仍可用，但真实 Bean 的 `@JustMock` 会替换定义并进入缓存 key。新测试优先用 `@JustTestProject`。
- 从较窄的手写启动类迁到 `@JustTestProject` 时，把原来的 `excludeAutoConfiguration` 与 mock 一并迁过去。
- 没有 `refresh` scope 时，框架会注册替代实现，`@RefreshScope` / `@Scope("refresh")` 的 Bean 可以直接加载。测试不提供真实 refresh 语义。不要为此再引入 Spring Cloud refresh，也不要自己覆盖框架的 scope 注册。
- `dataSource` 与 `transactionManager` 在名称空闲时注册为 JustTest 主 Bean 的别名；`dataSourceAliases` / `transactionManagerAliases` 给存量 `@Qualifier` / `@Transactional` 用。空白忽略、重复去重；目标名已被 Bean 或别名占用则跳过，不会覆盖。
- 声明了 `mapperPackages` 但缺少 mybatis-spring 会 fail-fast；有 mybatis-spring 但未声明时，不会猜测扫描根。
- 若出现名为 `client` 的 OkHttp Bean，与 `@Resource private XxxClient client` 冲突，由项目关闭 OkHttp，或改 `@Resource(name=…)` / 字段名。不要指望框架默认关掉 OkHttp。

## 用例目录与 Java glue

默认 case 根是测试类所在包。YAML 是 `.java` 的同级兄弟，不要再套一层简单类名。

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

- 一个业务类目录下放抽象 `Support`（共享 mock / 生命周期）。Support **不要**标 `@JustTest`，**不要**放 case YAML。
- 每个业务方法一个子包、一个**自身标注** `@JustTest` 的具体类、一个 `@CaseSource`。
- 同一包有多个直接标注 `@JustTest` 的具体类会 fail-fast。仅从 Support 继承 marker、具体类自身未标注，不能准入。
- `@CaseSource` 方法是 `void`。`response.yaml` 只看 `context.setResult(...)`，漏写按 `null` 断言。不要混 `@Test`，不要在同一类上堆多个 `@CaseSource`。
- YAML 与测试类放在同一棵 `src/test/java` 树时，POM 的 `testResources` 必须收录 `**/*.yaml` 与 `**/*.yml`。`src/test/resources` 留给 `schema.sql` / `application-test.yml`。
- `@CaseSource("custom-root")` 解析为 `{packagePath}/{custom-root}/`。根为空会 fail-fast，不会静默回退。

```java
abstract class OrderJustTestSupport implements JustTestLifecycle {

    @Autowired
    protected OrderService orderService;

    @JustMock
    protected PricingClient pricingClient;

    @Override
    public void beforeExecute(CaseContext context) {
        when(pricingClient.quote(anyLong()))
                .thenReturn(context.getObject("quote", Quote.class));
    }
}

@JustTest
class CreateJustTest extends OrderJustTestSupport {

    @BeforeCase("dup")
    void stubRejected() {
        when(pricingClient.quote(anyLong())).thenThrow(new PricingUnavailableException());
    }

    @CaseSource
    void createOrder(CaseContext context) {
        context.setResult(orderService.create(context.getLong("customerId")));
    }
}
```

稳定公开 API 仅限 `annotation`（含 `@JustSpy`）、`CaseContext`、`JustTestLifecycle`、`StaticMockContext`，以及 Boot 模块的 `@JustTest` / `@JustTestProject`。静态 Context / 工厂入口用 `configureStaticMocks` + `StaticMockContext`。

## 替身与共享上下文

在 `@JustTestProject` 下，指向真实 Bean 的 `@JustMock` 是按线程拦截，不是替换定义，也不进缓存 key。同一启动类下，字段子集不同的测试共用一个 Context。

- 当前测试类声明了该类型时，未 stub 返回 Mockito 默认值；没声明的类仍走真实 Bean。case 结束重置 stub。
- 类型全集在该共享 Context 第一次启动前确定，之后不能再增加。来源：`@JustTestProject(interceptMocks)`，以及该启动类所在包树内、解析到同一启动类的 `@JustTest` 上的真实 Bean `@JustMock`。
- 包树之外的测试若需要拦截，必须把类型写进 `interceptMocks`。没装上的类型 fail-fast，不会退回替换，也不会拆开缓存。
- 同一个真实 Bean 可以同时按接口和实现类 `@JustMock`：按解析到的 Bean 名只注册一次拦截，声明了任一类型即激活。不要为了这种混用去改声明；不能安全合并则期望启动期 fail-fast。
- 真实 Bean 内部的 `this.foo()` 不会进拦截。final 或当前 mock-maker 不能代理的类型会在启动期失败。
- 目标已经由 `autoMockFeignClients` 或 `@ThreadScopedMock` 做成线程级 mock 时，`@JustMock` 只绑定，不新建 Context。
- 同一类型多个候选 Bean 会在启动期 fail-fast。
- 激活看的是声明，不是当前有没有 stub。
- 同类型多 Bean 时用显式 `name` 或 `@Qualifier`，只命中类型兼容的 Bean。名称相同但类型无关的 Bean 不会被替换。错误的显式名称按缺少候选失败。
- 框架会命中已有定义（包括 MyBatis `MapperFactoryBean`），不会再生成 `#0` 双 Bean。不要为此再手写一份同类型 Bean。
- 与某个 `*AutoConfiguration` 的同类型 Bean 共存时，启动期 fail-fast 一次：列出冲突类型、候选 Bean 名和来源。JUnit 中止该类，后续 YAML case 不会反复刷新同一个失败 Context。下一步是补 `excludeAutoConfiguration`，或删掉多余 mock。不要指望框架静默让 mock 压过生产 Bean。

`@JustSpy` 只包裹已有 Bean，未 stub 的方法走真实逻辑（包括 `prepare.yaml` / H2），并且仍然进入缓存 key。Bean 选择规则与 `@JustMock` 相同。

- 必须用 `doReturn` / `doThrow` / `doNothing`。`when(spy.foo())` 会先调用真实方法。
- 找不到 Bean 时启动失败，不会自动创建。同一字段不能同时标 `@JustMock` 和 `@JustSpy`。同一类型或解析到同一个 Bean 时，也不能再被 `@JustMock` / `@ThreadScopedMock` 替换，启动期 fail-fast 一次。
- 真实 Bean 内部的 `this.bar()` 不会被 spy 拦截。若 Bean 是 `@Transactional` 等 AOP 代理，未 stub 的调用仍走代理。
- 线程作用域实例不会再被自动代理包一层；advisor 留在真实 Bean 上，测试字段可以直接 stub。
- MyBatis Mapper 与 Feign 这类 JDK 代理用 `delegatesTo`，不要用 `Mockito.spy`。

外部依赖必须在 `beforeExecute` 或 `@BeforeCase("case-name")` 里给出明确 stub，不要把未定义返回值交给业务 fail-open。

## H2 与 SQL

- 必须提供 `src/test/resources/sql/schema.sql`。对 MySQL dump，清理器只剥离常见附加语法（如 `ROW_FORMAT`、`UNSIGNED`、`ON UPDATE CURRENT_TIMESTAMP` 等），不覆盖全部 MySQL 方言。不要假定任意生产 DDL 都能原样跑在 H2 上。
- MyBatis 测试 SQL 只做窄改写：`IF(...)` → `CASEWHEN(...)`，`DATE_FORMAT(...)` → `FORMATDATETIME(...)`。纯 `JdbcTemplate` SQL 不走这条路径，不会被改写。
- 新 SQL 使用标准单引号。双引号字符串只是兼容历史 Mapper 的过渡能力。
- 默认会缓存清理后的 DDL，并从模板库克隆 schema。不要给 case H2 URL 加 `DB_CLOSE_DELAY=-1` 来掩盖连接已关闭。
- 框架拥有 H2 时，`JdbcTemplate` 或路由数据源缺失是配置错误，会 fail-fast，不会跳过 prepare / verify。

## YAML 与 Flag

| 文件 | 用途 |
| --- | --- |
| `request.yaml` | 用 `CaseContext` 的 `getString` / `getLong` / `getInt` / `getObject` / `getList` / `getMap` 读输入 |
| `prepare.yaml` | case 执行前插入的行 |
| `response.yaml` | 预期返回值，只与 `setResult` 比较 |
| `expect.yaml` | 预期数据库行 |
| `expect_exception.yaml` | 预期异常类型及可选消息 |

- 同一阶段可以用 `.yaml` 或 `.yml`。两种后缀同时存在时 fail-fast，不会静默选其中一个。

| Flag | 用途 |
| --- | --- |
| `[C]` | `expect.yaml` 行定位；`response.yaml` List 无序匹配 |
| `[CN]` | 仅 `expect.yaml`：定位行必须不存在 |
| `[N]` | 跳过字段；在 `prepare.yaml` 中不插入该列 |
| `[R]` | 正则匹配 |
| `[A]` | 非空 |
| `[D]` / `[D60]` | 实际时间距现在 N 秒内（默认 60）；YAML 期望值不参与比较 |
| `[J]` | JSON 结构比较 |
| `[F]` | 仅 `prepare.yaml`：原始库函数，如 `NOW()` |

- `[C]` 一旦用在 List 上，每个预期元素都必须是对象，且至少有一个 `[C]` 字段。库表断言优先写明确的 `[C]`。`[C]` 命中多行时失败，收紧到唯一一行。
- `[J]` 比较 JSON 结构。数值按 BigDecimal 比较，`1` 与 `1.0` 相等。解析失败会报错，不会退回字符串比较。
- 对象里单独的 `[N]:` 只跳过这个键，不会按键序跳过后面的字段。
- YAML 重复 key 在解析时失败。`expect_exception.yaml` 存在但为空或没有 `type` 是配置错误，不会吞掉任意异常。
- Flag 区分大小写。未知、拼错或用错阶段（如 `[cn]`、`[CC]`）会立即失败，错误里有字段名、case 路径和该阶段合法 Flag。
- 不要在 `@JustTest` 类或 `@CaseSource` 方法上使用 Spring `@Transactional` / `@Sql`。用 `prepare.yaml` / `expect.yaml`。
- 业务代码仍必须在所有路径 commit / rollback。case 边界若检测到未结束的 Spring 事务，即使匹配 `expect_exception.yaml` 也会失败；框架隔离不是业务 rollback 的替代品。

## 并发（消费工程）

并行由消费工程的 JUnit 配置控制。推荐先让测试类并行，同一类中的 case 保持串行：

```properties
# src/test/resources/junit-platform.properties
junit.jupiter.execution.parallel.enabled=true
junit.jupiter.execution.parallel.mode.default=same_thread
junit.jupiter.execution.parallel.mode.classes.default=concurrent
```

- 业务 static 状态、共享资源和异步线程都确认安全后，再把 `mode.default` 改为 `concurrent`。
- 不要复制 just-test 仓库里契约测试的 `fixed.parallelism=2`。
- 不要用 `ReflectionTestUtils` 把 raw Mockito mock 写入业务 Bean，覆盖 `@JustMock` 的 `ThreadScope` proxy。
- 不要给 case H2 URL 加 `DB_CLOSE_DELAY=-1` 来掩盖 `already closed`。
- 静态 Mock 只作用于当前 case 线程，不会传播到业务自行创建的异步线程。
- `@TestInstance(PER_CLASS)` 不能与类内并发 case 组合。

## 写出前核对

- [ ] 只依赖 `just-test-boot2` 或 `just-test-boot3` 之一，已发布版本 `1.2.9`，`scope` 为 `test`，没有 `just-test-core`。
- [ ] 用例从需求或接口的入口方法出发，不按内部方法拆单测，也不写端到端。
- [ ] 有 `src/test/resources/sql/schema.sql`，且启动类不加载生产 DataSource / 事务管理器。不要假设全部 MySQL 方言都能直接跑在 H2 上；新 SQL 用单引号。
- [ ] 没有为 `@RefreshScope` 再引入 Spring Cloud refresh，也没有自己覆盖框架的 scope 注册。
- [ ] `@JustTestProject` 在独立测试包，`@JustTest` 类在该包或子包。
- [ ] 没有默认关闭 OkHttp。`autoMockFeignClients` 仅在需要时打开，且只覆盖 `@FeignClient`。若 OkHttp 注册了名为 `client` 的 Bean 并与 `@Resource` 字段冲突，改字段名或显式 `name`，不要让框架关传输层。
- [ ] 存量数据源 / 事务别名已按需声明；已被占用的名字不会被覆盖。
- [ ] 非 Feign 外部依赖有显式 `@JustMock` 或 `@ThreadScopedMock`，并在 `beforeExecute` / `@BeforeCase` 里给出明确 stub。
- [ ] 包树外需要拦截的真实 Bean 类型已经写入 `interceptMocks`。没有在该共享 Context 第一次启动后再追加类型。
- [ ] 同一个真实 Bean 的接口 / 实现 `@JustMock` 可以并存；按 Bean 名只拦截一次。不能安全合并时期望启动期 fail-fast。
- [ ] 具体类自身有 `@JustTest`。抽象 Support 没有 `@JustTest`，也没有 case YAML。
- [ ] 一个具体类只有一个 `@CaseSource`，方法为 `void`；需要断言结果时调用了 `setResult`。
- [ ] YAML 是 sibling 目录，没有套 `{SimpleClassName}/`，并且 `testResources` 收录了 yaml。
- [ ] 没有用 `ReflectionTestUtils` 替换 `ThreadScope` proxy，也没有给 case URL 加 `DB_CLOSE_DELAY`。
- [ ] 没有叠加 `@SpringBootTest`、`@Test`、`@Transactional`、`@Sql`。
- [ ] `@JustSpy` 使用 `doReturn` / `doThrow` / `doNothing`。
- [ ] 没有依赖 `com.just.test.internal` 或 `JustTestClassValidationExtension`。

# AI 编写 JustTest 用例

JustTest 面向 **YAML 用例 + 薄 Java glue**，不是手写 assert 堆。本文是给任意模型/代理的配方；产品细节以 [README.zh-CN.md](../README.zh-CN.md) / [README.md](../README.md) 为准。

不要编造公司业务包、表结构或用例。在本仓库改框架时，演示只放 `src/contract-test` 的 `com.just.test.demo`。

## 选坐标

只选一条产品线，`scope` 用 `test`：

| 消费工程 | Maven Central |
| --- | --- |
| Java 8 / Spring Boot 2 | `io.github.cyaquarius:just-test-boot2:1.2.0` |
| Java 17 / Spring Boot 3 | `io.github.cyaquarius:just-test-boot3:1.2.0` |

不要同时引入两个顶层 artifact。不要单独声明 `just-test-core`：它已 shade 进 boot JAR，Central 不上架。

## 工程准备

消费工程必须提供：

1. `src/test/resources/sql/schema.sql`，用于初始化 H2。
2. 专用 `JustTestApplication`，推荐薄注解形式：

```java
@JustTestProject(
    basePackages = "com.example",
    mapperPackages = "com.example.mapper", // 可选
    dataSourceAliases = "masterDataSource", // 可选存量 Bean 名
    transactionManagerAliases = "masterDataTransactionManager"
    // autoMockFeignClients = true // 按需：只自动 mock @FeignClient
    // autoMockFeignClientExcludes = { BillingClient.class }
)
public class JustTestApplication {
}
```

放入独立测试包，所有 `@JustTest` 类放在该包或其子包。框架拥有默认扫描 denylist，**不**排除 `FeignAutoConfiguration` / `FeignContext`，也不默认关闭 OkHttp（传输层由项目自行选择；若出现名为 `client` 的 OkHttp Bean 与 `@Resource private XxxClient client` 冲突，项目可自行关 OkHttp 或改 `@Resource(name=…)` / 字段名）。可选 MyBatis 装配仍由 `mapperPackages` 声明。项目只声明 `basePackages` / 差异过滤器、需要兼容的数据源 / 事务别名，以及是否 `autoMockFeignClients`。`dataSource` 与 `transactionManager` 在名称空闲时始终注册；额外名称空白忽略、重复去重，已被 Bean 定义或别名占用则跳过。Redis / OSS / SDK / 自定义 Client 的 Mock 放在 Support 的 `@JustMock`（或 `@ThreadScopedMock`）上。只有打开 `autoMockFeignClients` 时，框架才按 `@FeignClient` 注解存在自动 mock 接口；不按 `*Client` 名字推断。显式 `@JustMock` / `@ThreadScopedMock` 优先于自动 Feign mock。手写 `@SpringBootConfiguration` 启动类仍可用。

安装坐标仍用已发布的 `1.2.0`；`autoMockFeignClients` 在 `main` 上，待下次 patch。
3. `@JustTest` 已包含 Boot TestContext 与 `test` profile；不要再叠 `@SpringBootTest` 或 `@BootstrapWith`。启动配置不得加载生产 `DataSource` / 事务管理器。

三层职责（框架拥有 / 默认可覆盖 / 项目必须声明）见 README「编写测试」。用例目录仍是 Support + 方法包，不要改成别的 case 根。

## 用例目录：业务类目录 + 方法包

默认 case 根是测试类所在包。case 目录是 `.java` 的同级兄弟，不要再套一层简单类名。

推荐树：

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

classpath 对应 `com/example/justtest/order/create/{ok,dup,bad-input}/*.yaml|yml`。`CreateJustTest` 的包是 `com.example.justtest.order.create`，不是 `.../create/CreateJustTest/`。

规则：

- 一个业务类目录（如 `order/`）下放抽象 `Support`（共享 mock / 生命周期）。Support **不要**标 `@JustTest`，**不要**放 case YAML；不计入「一包一个具体类」。抽象类上标 `@JustTest` 会 fail-fast 并列出 FQCN。
- 每个业务方法一个子包、一个**自身标注** `@JustTest` 的具体类、一个 `@CaseSource`。同一类上多个 `@CaseSource` 会共享本包全部 sibling case，不推荐。
- 同一包有多个**直接标注** `@JustTest` 的具体类时启动 fail-fast，并列出冲突 FQCN。父包里的抽象 Support 不算。仅从 Support 继承 marker、具体类自身未标注，不能准入。
- 不要探测 `{package}/{SimpleClassName}/`。类名套娃不是默认布局。
- `@CaseSource("custom-root")` 解析为 `{packagePath}/{custom-root}/`；根为空同样 fail-fast，不会静默回退。

把 YAML 与测试类放在同一棵 `src/test/java` 树，并在消费工程 POM 增加：

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

`src/test/resources` 留给 `schema.sql` / `application-test.yml`。YAML 也可以按同一包路径放在 `src/test/resources`；发现路径不变。每个 case 子目录按需要放 yaml，不必五个文件都写。

## Java glue

```java
// src/test/java/com/example/justtest/order/OrderJustTestSupport.java
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

// src/test/java/com/example/justtest/order/create/CreateJustTest.java
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

要点：

- 具体测试类必须**自身**标注 `@JustTest`，并 `implements JustTestLifecycle`（生命周期与 mock 可经由父包 Support 继承；`@JustTest` 不能写在 Support / 抽象基类上）；可执行方法只能一个 `@CaseSource`，不要混 `@Test`。
- `@CaseSource` 方法是 `void`。框架**不**采集 Java 返回值；`response.yaml` / `verifyResult` 只看 `context.setResult(...)`。漏写按 `null` 断言。
- 外部依赖在 `beforeExecute` 或 `@BeforeCase("case-name")` 里 stub；不要把未定义返回值交给业务 fail-open。
- 替换 Spring Bean 用 `@JustMock`；静态 Context / 工厂入口用 `configureStaticMocks` + `StaticMockContext`。
- 稳定公开 API 仅限 `annotation`、`CaseContext`、`JustTestLifecycle`、`StaticMockContext`，以及 Boot 模块的 `@JustTest` / `@JustTestProject`。不要依赖 `com.just.test.internal`，也不要直接使用 `JustTestClassValidationExtension`。

## Flag 速查

| Flag | 用途 |
| --- | --- |
| `[C]` | `expect.yaml` 行定位；`response.yaml` List 无序匹配 |
| `[CN]` | 仅 `expect.yaml`：定位行必须不存在 |
| `[N]` | 跳过字段；在 `prepare.yaml` 中不插入该列 |
| `[R]` | 正则匹配 |
| `[A]` | 非空 |
| `[D]` / `[D60]` | 实际时间距*现在* N 秒内（默认 60）；YAML 期望值不参与比较 |
| `[J]` | JSON 结构比较 |
| `[F]` | 仅 `prepare.yaml`：原始库函数，如 `NOW()` |

`[C]` 一旦用在 List，每个预期元素都必须是对象且至少有一个 `[C]` 字段。库表断言优先写明确的 `[C]`。

## 反模式

- 同时引入 `just-test-boot2` 与 `just-test-boot3`，或单独声明 `just-test-core`。
- 依赖 `com.just.test.internal`，或直接使用 `JustTestClassValidationExtension`。
- 用 `ReflectionTestUtils` 把 raw Mockito mock 写入业务 Bean，覆盖 `@JustMock` 的 `ThreadScope` proxy。
- 在 `@JustTest` 类或 `@CaseSource` 方法上使用 Spring `@Transactional` / `@Sql`（生命周期早于 case 绑定，框架 fail-fast）。用 `prepare.yaml` / `expect.yaml`。
- 再叠 `@SpringBootTest`、普通 `@Test`，或不写 `context.setResult` 却期望 `response.yaml` 对上返回值。
- 再套一层 `{SimpleClassName}/`，在同一包放多个具体 `@JustTest` 类，或给抽象 Support 放 case YAML / 标 `@JustTest`。
- 同一类上堆多个 `@CaseSource`，或让多个类共享同一个无差别 `@CaseSource` 自定义根。

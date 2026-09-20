# AI 编写 SmartTest 用例

SmartTest 面向 **YAML 用例 + 薄 Java glue**，不是手写 assert 堆。本文是给任意模型/代理的配方；产品细节以 [README.zh-CN.md](../README.zh-CN.md) / [README.md](../README.md) 为准。

不要编造公司业务包、表结构或用例。在本仓库改框架时，演示只放 `src/contract-test` 的 `com.just.test.smarttest.demo`。

## 选坐标

只选一条产品线，`scope` 用 `test`：

| 消费工程 | Maven Central |
| --- | --- |
| Java 8 / Spring Boot 2 | `io.github.cyaquarius:just-test-boot2:1.0.0` |
| Java 17 / Spring Boot 3 | `io.github.cyaquarius:just-test-boot3:1.0.0` |

不要同时引入两个顶层 artifact。不要单独声明 `just-test-core`：它已 shade 进 boot JAR，Central 不上架。

## 工程准备

消费工程必须提供：

1. `src/test/resources/sql/schema.sql`，用于初始化 H2。
2. 专用 `SmartTestApplication`（`@SpringBootConfiguration` + `@EnableAutoConfiguration` + `@ComponentScan`，扫描时排除生产 `Application`）。放入独立测试包，所有 `@SmartTest` 类放在该包或其子包。
3. `@SmartTest` 已包含 Boot TestContext 与 `test` profile；不要再叠 `@SpringBootTest` 或 `@BootstrapWith`。启动配置不得加载生产 `DataSource` / 事务管理器。

完整启动类示例见 README「编写测试」。

## 用例目录：业务类目录 + 方法包

默认 case 根是测试类所在包。case 目录是 `.java` 的同级兄弟，不要再套一层简单类名。

推荐树：

```text
src/test/java/com/example/smarttest/order/
├── OrderSmartTestSupport.java
├── create/
│   ├── CreateSmartTest.java
│   ├── ok/
│   ├── dup/
│   └── bad-input/
└── cancel/
    ├── CancelSmartTest.java
    └── ok/
```

classpath 对应 `com/example/smarttest/order/create/{ok,dup,bad-input}/*.yaml|yml`。`CreateSmartTest` 的包是 `com.example.smarttest.order.create`，不是 `.../create/CreateSmartTest/`。

规则：

- 一个业务类目录（如 `order/`）下放抽象 `Support`（共享 mock / 生命周期）。Support **不要**标 `@SmartTest`，**不要**放 case YAML；不计入「一包一个具体类」。
- 每个业务方法一个子包、一个具体 `@SmartTest` 类、一个 `@CaseSource`。同一类上多个 `@CaseSource` 会共享本包全部 sibling case，不推荐。
- 同一包有多个具体 `@SmartTest` 类时启动 fail-fast，并列出冲突 FQCN。父包里的抽象 Support 不算。
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
// src/test/java/com/example/smarttest/order/OrderSmartTestSupport.java
abstract class OrderSmartTestSupport implements SmartTestLifecycle {

    @Autowired
    protected OrderService orderService;

    @SmartMock
    protected PricingClient pricingClient;

    @Override
    public void beforeExecute(CaseContext context) {
        when(pricingClient.quote(anyLong()))
                .thenReturn(context.getObject("quote", Quote.class));
    }
}

// src/test/java/com/example/smarttest/order/create/CreateSmartTest.java
@SmartTest
class CreateSmartTest extends OrderSmartTestSupport {

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

- 具体测试类必须 `@SmartTest` + `implements SmartTestLifecycle`（可经由父包 Support）；可执行方法只能一个 `@CaseSource`，不要混 `@Test`。
- `@CaseSource` 方法是 `void`。框架**不**采集 Java 返回值；`response.yaml` / `verifyResult` 只看 `context.setResult(...)`。漏写按 `null` 断言。
- 外部依赖在 `beforeExecute` 或 `@BeforeCase("case-name")` 里 stub；不要把未定义返回值交给业务 fail-open。
- 替换 Spring Bean 用 `@SmartMock`；静态 Context / 工厂入口用 `configureStaticMocks` + `StaticMockContext`。
- 稳定公开 API 仅限 `annotation`、`CaseContext`、`SmartTestLifecycle`、`StaticMockContext`，以及 Boot 模块的 `@SmartTest`。不要依赖 `com.just.test.smarttest.internal`，也不要直接使用 `SmartTestClassValidationExtension`。

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
- 依赖 `com.just.test.smarttest.internal`，或直接使用 `SmartTestClassValidationExtension`。
- 用 `ReflectionTestUtils` 把 raw Mockito mock 写入业务 Bean，覆盖 `@SmartMock` 的 `ThreadScope` proxy。
- 在 `@SmartTest` 类或 `@CaseSource` 方法上使用 Spring `@Transactional` / `@Sql`（生命周期早于 case 绑定，框架 fail-fast）。用 `prepare.yaml` / `expect.yaml`。
- 再叠 `@SpringBootTest`、普通 `@Test`，或不写 `context.setResult` 却期望 `response.yaml` 对上返回值。
- 再套一层 `{SimpleClassName}/`，在同一包放多个具体 `@SmartTest` 类，或给抽象 Support 放 case YAML / 标 `@SmartTest`。
- 同一类上堆多个 `@CaseSource`，或让多个类共享同一个无差别 `@CaseSource` 自定义根。

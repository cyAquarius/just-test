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

## 用例目录：方法包 + 本包为 case 根

推荐默认（YAML 与测试类同目录）：

```text
src/test/java/com/example/smarttest/order/     ← 业务类目录
├── OrderSmartTestSupport.java                 ← 抽象基类（共享 mock / 生命周期）
├── create/                                    ← 方法包
│   ├── CreateSmartTest.java                   ← 一个 @CaseSource；继承 Support
│   ├── ok/                                    ← case 目录与 .java 同级
│   ├── dup/
│   └── bad-input/
└── cancel/
    ├── CancelSmartTest.java
    ├── ok/
    └── ...
```

case 根是**方法测试类所在包**（classpath `.../order/create`），不是 `.../create/CreateSmartTest/`。默认发现 `{packagePath}/*/*.yaml|yml`。

`schema.sql` / `application-test.yml` 留在 `src/test/resources`。在消费工程 `pom.xml` 增加：

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

**为什么本包就是 case 根：** 同一层目录只保留一种语义——子目录都是 case。同一包只允许一个具体 `@SmartTest` 类，多了启动即失败并列出冲突类名。抽象 Support 可以共存。不要在同一个类上写多个 `@CaseSource`（它们会共享本包全部 sibling case）。

**逃生口：** `@CaseSource("custom-root")` 解析为 `{packagePath}/{custom-root}/`。需要旧的类名目录时，把 value 写成简单类名。自定义根缺失同样 fail-fast，不会再猜测类名子目录或回退到包目录。

每个 case 子目录按需要放 yaml，不必五个文件都写。

## Java glue

```java
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

@SmartTest
class CreateSmartTest extends OrderSmartTestSupport {

    @BeforeCase("bad-input")
    void stubRejected() {
        when(pricingClient.quote(anyLong())).thenThrow(new PricingUnavailableException());
    }

    @CaseSource
    void create(CaseContext context) {
        context.setResult(orderService.create(context.getLong("customerId")));
    }
}
```

要点：

- 具体测试类必须 `@SmartTest` + `implements SmartTestLifecycle`（可继承 Support）；可执行方法只能有一个 `@CaseSource`，不要混 `@Test`。
- Support 基类保持 abstract，不要标 `@SmartTest`（标了也会被跳过，但不需要）。
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
- 在同一个包里放多个具体 `@SmartTest` 类，或在同一个类上写多个 `@CaseSource`。
- 再套一层类名目录当作默认布局；类名目录只应通过显式 `@CaseSource("SimpleClassName")` 作为逃生口。
- 让多个类共享同一个无差别 `@CaseSource` 自定义根。

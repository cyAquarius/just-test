# just-test

just-test 是一个面向自动化测试的通用工具库。当前提供 SmartTest：基于 Spring、H2、JUnit 5 和 YAML 的数据驱动集成测试框架。

## 兼容范围

- Java 8
- Spring Boot 2.7.18
- JUnit 5.9.3
- H2 2.x

框架代码位于 `com.just.test.smarttest`，业务项目只应在测试范围内引入本依赖。

## 构建与本地安装

```bash
mvn clean verify
mvn clean install
```

构建和测试均使用 Maven，产物默认安装到本机 `~/.m2`。

## 引入依赖

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/cyaquarius/just-test</url>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>

<dependency>
    <groupId>com.just.test</groupId>
    <artifactId>just-test</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

GitHub Packages 中的私有包需要认证。请在消费方的 `~/.m2/settings.xml` 中配置只读凭据，不要把 Token 写入项目：

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
    <servers>
        <server>
            <id>github</id>
            <username>cyAquarius</username>
            <password>${env.GITHUB_PACKAGES_TOKEN}</password>
        </server>
    </servers>
</settings>
```

`GITHUB_PACKAGES_TOKEN` 应使用具有 `read:packages` 权限的 classic PAT。私有源码仓库的消费场景还需要 `repo` 权限。

## 编写测试

业务项目需要提供 `src/test/resources/sql/schema.sql`，框架会使用它初始化 H2 表结构。

```java
@SmartTest
@ContextConfiguration(classes = YourApplication.class)
class OrderServiceTest implements SmartTestLifecycle {

    @Autowired
    private OrderService orderService;

    @ParameterizedTest
    @CaseSource
    void createOrder(CaseContext context) {
        Object result = orderService.create(context.getLong("customerId"));
        context.setResult(result);
    }
}
```

每个测试类的 YAML 用例默认放在与测试类包名和类名对应的目录中：

```text
src/test/resources/com/example/order/OrderServiceTest/
├── normal/
│   ├── request.yaml
│   ├── prepare.yaml
│   ├── response.yaml
│   └── expect.yaml
└── failure/
    ├── request.yaml
    ├── prepare.yaml
    └── expect_exception.yaml
```

- `request.yaml`：测试输入。
- `prepare.yaml`：执行前写入 H2 的数据。
- `response.yaml`：预期返回值。
- `expect.yaml`：预期数据库状态。
- `expect_exception.yaml`：预期异常。

生命周期为：清理脏表 → 加载 `prepare.yaml` → 执行 `@BeforeCase` → 执行测试 → 验证返回值、异常和数据库。

## 发布

推送到 `main` 后，GitHub Actions 会使用仓库自带的 `GITHUB_TOKEN` 自动执行 Java 8 构建，并将当前版本发布到 GitHub Packages。也可以在 Actions 页面手动触发发布工作流。

仓库只包含可复用框架代码和最小演示，不应加入具体业务包、业务表结构或业务测试用例。

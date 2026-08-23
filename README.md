# just-test

[中文文档](README.zh-CN.md)

`just-test` is a reusable Java test toolkit. Its current module, SmartTest, provides YAML-driven Spring Boot integration tests backed by JUnit 5 and an isolated in-memory H2 database.

It is deliberately a test-scope framework: it supplies repeatable test setup, assertions, and test doubles, but it does not replace application design, production database compatibility testing, or concurrency control in application code.

## Compatibility

- Java 8
- Spring Boot 2.7.18
- JUnit 5.9.3
- H2 2.x
- Maven

## What SmartTest provides

- `@SmartTest` configures Spring Test, H2, `JdbcTemplate`, and a transaction manager so application services can exercise their normal transaction behavior.
- `@CaseSource` discovers YAML cases and creates one full JUnit test-template invocation per case, with `CaseContext` available to the test and standard per-test lifecycle methods.
- `prepare.yaml`, `response.yaml`, `expect.yaml`, and `expect_exception.yaml` cover data setup and result, database, and exception verification.
- `@SmartMock` creates a thread-scoped Mockito mock. When several beans share a type, an explicit `name` wins; otherwise SmartTest applies Spring autowire and qualifier rules, then resolves `@Primary`, field name or alias, and finally a unique candidate.
- `@ThreadScopedMock` applies the same scoped-mock model to an annotated `@Bean` method.
- `StaticMockContext` can replace an application static context/factory gateway per case thread and restores it automatically at case end.
- The H2 test database is isolated per SmartTest `ApplicationContext` and active case; each case database is released at case end, and schema initialization is retried after failure.
- MyBatis test SQL receives narrowly scoped MySQL-to-H2 rewrites: `IF(...)` becomes `CASEWHEN(...)`; legacy double-quoted string literals are supported inside known string functions and on the right side of comparison operators.

## Boundaries and concurrency

SmartTest isolates the test resources it owns. It does **not** make arbitrary application code globally parallel-safe.

- Static registries initialized by application code remain an application concern.
- Case static mocks affect only the current thread. They are active for user `@BeforeEach`, the test, and user `@AfterEach`; they cannot cover Spring context refresh or Spring Test listeners that run before the case invocation, and do not propagate to application-created asynchronous threads.
- When multiple Spring contexts are detected, SmartTest emits one risk warning. A related case failure adds a diagnostic log without replacing the original exception. The presence of an arbitrary static mock does not suppress this hint because the framework cannot know whether it covers the relevant gateway.
- New and legacy test classes can coexist; legacy classes without `@SmartTest` do not activate the SmartTest lifecycle. If both run in parallel while application code shares a JVM-static ContextHolder or factory, SmartTest cannot protect the legacy test thread. Keep affected legacy tests serial or migrate their static gateway.
- Manually created threads, `CompletableFuture` common-pool tasks, and executors not managed by SmartTest do not receive mock or database context automatically; database access without an active case fails fast instead of creating an empty H2 database.
- Test-level Spring `@Transactional` and `@Sql` are not supported for `@CaseSource` methods: their lifecycle runs before a case is bound. Use `prepare.yaml` and `expect.yaml` for deterministic case data instead.
- A passing rerun is not proof of concurrency safety. Keep flaky suites serial until their ownership and lifecycle boundaries are established.
- Write new SQL with standard single-quoted strings. The double-quote rewrite is only a compatibility bridge for existing MySQL mapper SQL.

## Add the dependency

Use this library only in test scope:

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

GitHub Packages requires credentials in the consumer's `~/.m2/settings.xml`. Keep the token outside the project, for example `${env.GITHUB_PACKAGES_TOKEN}`. A classic PAT needs `read:packages`; private-repository consumers also need `repo`.

## Write a test

SmartTest always uses the Spring Boot TestContext. The consumer project must provide `src/test/resources/sql/schema.sql` for H2 schema initialization and a dedicated test startup configuration:

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

`SmartTestApplication` is the consumer-owned startup configuration dedicated to SmartTest and needs no `main` method. Put it in a dedicated test package and place every SmartTest class in that package or a child package, for example `com.example.smarttest.order`. Spring Boot finds this nearer test configuration before searching the parent package that contains the production `Application`. Merely placing it under `src/test` does not prevent a conflict because the test classpath contains both production and test classes. When scanning the application root package, exclude the production startup class as shown so component scanning does not load its configuration again. The consumer owns any additional component-scan exclusions, mapper wiring, and project-level external-dependency mocks.

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

`@SmartTest` already includes the Spring Boot bootstrapper and loads `application-test.yml` through the `test` profile. Do not combine it with `@SpringBootTest` or declare another `@BootstrapWith`. The dedicated test package should expose exactly one `@SpringBootConfiguration`. Use `@ContextConfiguration(classes = SmartTestApplication.class)` only when a test is outside that package hierarchy, several startup configurations are candidates, or that test needs a special configuration.

Use `@SmartMock(name = "beanName")` or `@Qualifier("beanName")` when a type has multiple candidates. Prefer `@SmartMock` when replacing Spring beans; `@MockBean` is not required.

If application code obtains Spring through a static gateway, bind that gateway explicitly per case:

```java
@Override
public void configureStaticMocks(CaseContext context, StaticMockContext mocks) {
    MockedStatic<SpringContextHolder> holder = mocks.mockStatic(SpringContextHolder.class);
    holder.when(SpringContextHolder::getApplicationContext)
          .thenReturn(mocks.getApplicationContext());
}
```

Unstubbed static methods continue to call their real implementation. Do not close the returned `MockedStatic` manually; SmartTest closes all registrations after user `@AfterEach` on the original case thread.

This feature requires the consumer to enable Mockito's inline mock maker explicitly. For example, put `mock-maker-inline` in the consumer's `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`, or add `mockito-inline` matching its Mockito version. SmartTest does not select a MockMaker from its published JAR, avoiding conflicts with existing Mockito or PowerMock configuration.

By default, cases live below the test class package and simple name:

```text
src/test/resources/com/example/order/OrderServiceTest/
└── create-order/
    ├── request.yaml
    ├── prepare.yaml
    ├── response.yaml
    ├── expect.yaml
    └── expect_exception.yaml
```

`@CaseSource("custom-root")` uses a custom root below the test class package. If the default class-name directory is absent, SmartTest remains compatible with the older package-level case layout.

## YAML files and flags

- `request.yaml`: input exposed through `CaseContext` (`getString`, `getLong`, `getInt`, `getObject`, `getList`, and `getMap`).
- `prepare.yaml`: rows inserted before the case.
- `response.yaml`: expected return value.
- `expect.yaml`: expected database rows.
- `expect_exception.yaml`: expected exception type and optional message.

Field suffix flags apply to response and database assertions:

| Flag | Meaning |
| --- | --- |
| `[C]` | Row selection key; also enables unordered list matching. |
| `[CN]` | Selected row must not exist. |
| `[N]` | Skip the field; in `prepare.yaml`, do not insert it. |
| `[R]` | Regular-expression match. |
| `[A]` | Non-null assertion. |
| `[D]` / `[D60]` | Timestamp tolerance in seconds (default: 60). |
| `[J]` | JSON structural comparison. |
| `[F]` | Raw database function in `prepare.yaml`, such as `NOW()`. |

For database expectations, prefer explicit `[C]` fields so the intended row is unambiguous.

## Lifecycle

`@SmartTest` is a class-level execution contract: every executable test method in the class must use `@CaseSource`. If the class contains `@Test`, `@RepeatedTest`, `@ParameterizedTest`, `@TestFactory`, or another ordinary JUnit test method, SmartTest fails before execution and asks you to split the class. For incremental adoption, leave existing JUnit test classes unchanged and put new cases in a separate `@SmartTest` class; legacy classes without `@SmartTest` are unaffected.

`@CaseSource` is itself the test annotation; do not combine it with another JUnit test annotation. For every YAML case SmartTest performs:

1. create an independent JUnit invocation and its `CaseContext`;
2. bind the case, initialize its schema, prepare a clean database, and load `prepare.yaml`;
3. reset and prewarm scoped mocks, inject `@SmartMock` fields, and configure case static mocks;
4. execute user `@BeforeEach` methods;
5. invoke matching `@BeforeCase("case-name")` methods and `beforeExecute`, execute the test, call `afterExecute`, and verify exception, result, and database data;
6. execute user `@AfterEach` methods;
7. close static and scoped mocks and release the case database, retaining cleanup failures without hiding the original test failure.

## Build and publish

```bash
mvn clean verify
mvn clean install
```

Pushing to `main` runs the Java 8 GitHub Actions workflow, which executes `mvn clean deploy` and publishes the configured version to GitHub Packages. This repository contains reusable framework code and minimal demos only; do not add product-specific packages, schemas, or tests.

# just-test

[中文文档](README.zh-CN.md)

`just-test` is a reusable Java test toolkit. Its current module, SmartTest, provides YAML-driven Spring integration tests backed by JUnit 5 and an isolated in-memory H2 database.

It is deliberately a test-scope framework: it supplies repeatable test setup, assertions, and test doubles, but it does not replace application design, production database compatibility testing, or concurrency control in application code.

## Compatibility

- Java 8
- Spring Boot 2.7.18
- JUnit 5.9.3
- H2 2.x
- Maven

## What SmartTest provides

- `@SmartTest` configures Spring Test, H2, `JdbcTemplate`, and a transaction manager so application services can exercise their normal transaction behavior.
- `@CaseSource` discovers YAML cases and passes a `CaseContext` into a parameterized JUnit test.
- `prepare.yaml`, `response.yaml`, `expect.yaml`, and `expect_exception.yaml` cover data setup and result, database, and exception verification.
- `@SmartMock` creates a thread-scoped Mockito mock. When several beans share a type, SmartTest resolves the target deterministically through `name`, `@Qualifier`, field name, `@Primary`, then a unique type candidate.
- `@ThreadScopedMock` applies the same scoped-mock model to an annotated `@Bean` method.
- The H2 test database is isolated per SmartTest `ApplicationContext` and active case; each case database is released at case end, and schema initialization is retried after failure.
- MyBatis test SQL receives narrowly scoped MySQL-to-H2 rewrites: `IF(...)` becomes `CASEWHEN(...)`; legacy double-quoted string literals are supported inside known string functions and on the right side of comparison operators.

## Boundaries and concurrency

SmartTest isolates the test resources it owns. It does **not** make arbitrary application code globally parallel-safe.

- Static registries initialized by application code remain an application concern.
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

The consumer project must provide `src/test/resources/sql/schema.sql` for H2 schema initialization.

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

Use `@SmartMock(name = "beanName")` or `@Qualifier("beanName")` when a type has multiple candidates.

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

For every YAML case SmartTest performs:

1. bind a unique case identity, initialize its database schema, and prepare a clean database;
2. load `prepare.yaml`;
3. reset and prewarm scoped mocks, then inject `@SmartMock` fields;
4. invoke matching `@BeforeCase("case-name")` methods and `beforeExecute`;
5. execute the JUnit method, then `afterExecute`;
6. verify exception, result, and database data unless the `SmartTestLifecycle` implementation opts out;
7. release scoped objects and the case database, then retain cleanup failures as suppressed exceptions without hiding the test failure.

## Build and publish

```bash
mvn clean verify
mvn clean install
```

Pushing to `main` runs the Java 8 GitHub Actions workflow, which executes `mvn clean deploy` and publishes the configured version to GitHub Packages. This repository contains reusable framework code and minimal demos only; do not add product-specific packages, schemas, or tests.

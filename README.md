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
- The H2 test database is isolated per SmartTest `ApplicationContext` and active case; cleaned DDL is cached, and the schema is cloned from a template database by default (disable with `smarttest.schema.clone=false`); each case database is released at case end, and schema initialization is retried after failure.
- MyBatis test SQL receives narrowly scoped MySQL-to-H2 rewrites: `IF(...)` becomes `CASEWHEN(...)`, and `DATE_FORMAT(...)` becomes `FORMATDATETIME(...)`; the `DATE_FORMAT` rewrite runs through the MyBatis `StatementHandler` interceptor path (the same path as `IF(...)` → `CASEWHEN(...)`), so plain `JdbcTemplate` SQL is not rewritten unless it goes through that interceptor. Legacy double-quoted string literals are supported inside known string functions and on the right side of comparison operators.

## Boundaries and concurrency

SmartTest isolates the test resources it owns. It does **not** make arbitrary application code globally parallel-safe.

- Static registries initialized by application code remain an application concern.
- Case static mocks affect only the current thread. They are active for user `@BeforeEach`, the test, and user `@AfterEach`; they cannot cover Spring context refresh or Spring Test listeners that run before the case invocation, and do not propagate to application-created asynchronous threads.
- When multiple Spring contexts are detected, SmartTest emits one risk warning. A related case failure adds a diagnostic log without replacing the original exception. The presence of an arbitrary static mock does not suppress this hint because the framework cannot know whether it covers the relevant gateway.
- New and legacy test classes can coexist; legacy classes without `@SmartTest` do not activate the SmartTest lifecycle. If both run in parallel while application code shares a JVM-static ContextHolder or factory, SmartTest cannot protect the legacy test thread. Keep affected legacy tests serial or migrate their static gateway.
- Manually created threads, `CompletableFuture` common-pool tasks, and executors not managed by SmartTest do not receive mock or database context automatically; database or thread-scoped mock access without an active case fails fast instead of creating an empty H2 database or an unstubbed mock.
- Spring `@Transactional` and `@Sql` are unsupported on a SmartTest class or `@CaseSource` method because their lifecycle runs before a case is bound; SmartTest fails fast on these configurations. Use `prepare.yaml` and `expect.yaml` for deterministic case data instead.
- A passing rerun is not proof of concurrency safety. Keep flaky suites serial until their ownership and lifecycle boundaries are established.
- Write new SQL with standard single-quoted strings. The double-quote rewrite is only a compatibility bridge for existing MySQL mapper SQL.

### Parallel execution configuration

The consumer project's JUnit configuration controls scheduling; SmartTest isolates framework-owned resources once tests run in parallel. Start by running test classes concurrently while keeping cases within each class sequential:

```properties
# src/test/resources/junit-platform.properties
junit.jupiter.execution.parallel.enabled=true
junit.jupiter.execution.parallel.mode.default=same_thread
junit.jupiter.execution.parallel.mode.classes.default=concurrent
```

After verifying that application static state, external shared resources, and asynchronous threads are safe, set `mode.default` to `concurrent` to run cases within the same class concurrently. Normal tests do not need `@Execution`; use `@Execution(ExecutionMode.SAME_THREAD)` only to downgrade an exceptional class that cannot satisfy the concurrency boundaries.

### Parallel rollout antipatterns

A parallel failure is not automatically a SmartTest isolation failure; first inspect the downstream project's test doubles, static state, and resource lifecycle. The following patterns can make usage errors look like framework flakes:

1. **Replacing an `@SmartMock` proxy with `ReflectionTestUtils`**
   - **Wrong:** In `beforeExecute`, use `ReflectionTestUtils` to write a raw Mockito mock into a business bean field, replacing the `ThreadScope` proxy created by `@SmartMock`. Business beans are often singletons, so this pollutes a singleton field and lets other threads or cases see the wrong stub.
   - **Right:** Stub only the test-class `@SmartMock` field and keep business beans wired to the scoped proxy. Use `StaticMockContext` / `configureStaticMocks` to configure static factory or context gateways per case.

2. **Adding a keep-alive flag to the case H2 URL**
   - **Wrong:** Add `DB_CLOSE_DELAY=-1` (or a similar keep-alive flag) to the case URL to hide `already closed`. This only defers the lifecycle problem and can cause memory growth, slower full runs, or OOM.
   - **Right:** Follow the resource boundary described in [#12 (`already closed`)](https://github.com/cyAquarius/just-test/issues/12): when switching cases, unbind thread-bound JDBC/MyBatis holders first, then release the old `DataSource` and rebuild the current case `DataSource`. The template database may use `DB_CLOSE_DELAY=-1` internally for schema cloning (see [#16](https://github.com/cyAquarius/just-test/issues/16)); that is not permission to add it to case URLs. The default case URL contains no `DB_CLOSE_DELAY`.

3. **Expecting SmartTest to repair application static registries**
   - **Wrong:** Blame SmartTest for concurrency pollution in an application static context, factory, or registry, expect the framework to repair it automatically, or default to adding a global `ContextCreationLock` upstream. SmartTest does not own the isolation or concurrency governance of those application static gateways.
   - **Right:** Use this rollout checklist:
     - Multiple Spring Context warning → check whether `StaticMockContext` / `configureStaticMocks` covers the relevant static context or factory gateway.
     - Diagnose Bean wiring issues such as the Mapper `#0` dual-bean case by name, qualifier, and startup configuration; see [#11](https://github.com/cyAquarius/just-test/issues/11) instead of hiding them with a global lock.
     - Tests that cannot be isolated stay serial: use `@Execution(ExecutionMode.SAME_THREAD)` or run the test class serially.
     - Do not default to adding a global `ContextCreationLock` upstream; fix the static gateway isolation first, and keep unisolated tests serial.

4. **Unstubbed external dependencies plus business fail-open**
   - **Wrong:** Leave critical external dependencies unstubbed while business code swallows an exception or treats it as success; parallel timing and return values can make this look like a framework flake.
   - **Right:** Stub critical collaborators in `beforeExecute` / `@BeforeCase` and assert success and exception paths explicitly; do not pass undefined external return values into business fail-open logic.

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

The artifact transitively provides the Spring Boot TestContext and auto-configuration APIs required by `@SmartTest` and the recommended configuration below. The consumer still selects and manages its own Spring Boot 2.x version.

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

The SmartTest context uses the H2 `DataSource`, transaction manager, and `JdbcTemplate` supplied by the framework. Its test startup configuration must not also load production `DataSource`, transaction-manager, or other database-infrastructure configurations; exclude them with the `test` profile or component-scan filters. The framework wires its own infrastructure explicitly, but does not rewrite user Bean `@Primary` metadata or choose between production and test data sources on the consumer's behalf.

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
Do not use `ReflectionTestUtils` to replace an `@SmartMock` `ThreadScope` proxy with a raw Mockito mock in a business bean field; stub only the test-class `@SmartMock` field.

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

This feature requires the consumer to enable Mockito's inline mock maker explicitly; the same applies when the actual Bean targeted by `@SmartMock` is a final class. For example, put `mock-maker-inline` in the consumer's `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`, or add `mockito-inline` matching its Mockito version. SmartTest does not select a MockMaker from its published JAR, avoiding conflicts with existing Mockito or PowerMock configuration.

By default, cases live below the test class package and simple name:

```text
src/test/resources/com/example/smarttest/order/OrderServiceSmartTest/
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

Field suffix flags are stage-specific: `[C]` selects rows in `expect.yaml` and enables unordered List matching in `response.yaml`. Once any List item uses `[C]`, every expected item must be an object with at least one `[C]` field; SmartTest rejects unordered expectations that mix keyed and unkeyed items. `[CN]` applies only to `expect.yaml`, `[F]` applies only to `prepare.yaml`, and the remaining flags apply to the corresponding preparation or assertion stage shown below:

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
2. bind the case, create and initialize a fresh case database, and load `prepare.yaml`;
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

Pull requests targeting `main` first run Java 8 `mvn clean verify`. After a push to `main`, the publishing workflow executes `mvn clean deploy` and publishes the configured version to GitHub Packages. This repository contains reusable framework code and minimal demos only; do not add product-specific packages, schemas, or tests.

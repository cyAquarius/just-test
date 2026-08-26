package com.just.test.smarttest.lifecycle;

import com.just.test.smarttest.annotation.BeforeCase;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.context.CaseExecutionContext;
import com.just.test.smarttest.datasource.SchemaInitializer;
import com.just.test.smarttest.datasource.SmartTestRoutingDataSource;
import com.just.test.smarttest.loader.DataSetLoader;
import com.just.test.smarttest.mock.SmartMockInjector;
import com.just.test.smarttest.mock.StaticMockContext;
import com.just.test.smarttest.mock.ThreadScopedMockRegistry;
import com.just.test.smarttest.scope.ThreadScope;
import com.just.test.smarttest.verifier.DataSetVerifier;
import com.just.test.smarttest.verifier.ExceptionVerifier;
import com.just.test.smarttest.verifier.ResultVerifier;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 单个 SmartTest case invocation 的完整生命周期。
 *
 * <p>执行顺序：case bind / schema / prepare / mock setup → JUnit {@code @BeforeEach}
 * → {@code @BeforeCase} / lifecycle / test / verify → JUnit {@code @AfterEach} → resource cleanup。</p>
 */
public final class SmartTestExtension implements BeforeEachCallback, AfterEachCallback,
        InvocationInterceptor, ParameterResolver {

    private static final Logger log = LoggerFactory.getLogger(SmartTestExtension.class);
    private static final String SCHEMA_LOCATION = "classpath:sql/schema.sql";
    private static final String SQL_SESSION_FACTORY_CLASS = "org.apache.ibatis.session.SqlSessionFactory";
    private static final String SQL_SESSION_HOLDER_CLASS = "org.mybatis.spring.SqlSessionHolder";
    private static final String SQL_SESSION_CLASS = "org.apache.ibatis.session.SqlSession";
    private static final ApplicationContextDiagnostics CONTEXT_DIAGNOSTICS =
            new ApplicationContextDiagnostics();

    private final CaseContext caseContext;
    private ApplicationContext applicationContext;
    private SmartTestRoutingDataSource routingDataSource;
    private StaticMockContext staticMockContext;
    private JdbcTemplate jdbcTemplate;
    private SmartTestLifecycle lifecycle;
    private boolean caseBound;
    private boolean exceptionVerificationEvaluated;
    private boolean exceptionVerifiedByLifecycle;

    public SmartTestExtension(CaseContext caseContext) {
        if (caseContext == null) {
            throw new IllegalArgumentException("caseContext must not be null");
        }
        this.caseContext = caseContext;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext,
                                     ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == CaseContext.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext) {
        return caseContext;
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        Object testInstance = extensionContext.getRequiredTestInstance();
        if (!(testInstance instanceof SmartTestLifecycle)) {
            throw new IllegalStateException(String.format(
                    "[SmartTest] %s must implement SmartTestLifecycle",
                    testInstance.getClass().getName()));
        }
        lifecycle = (SmartTestLifecycle) testInstance;
        applicationContext = SpringExtension.getApplicationContext(extensionContext);
        CONTEXT_DIAGNOSTICS.observe(applicationContext);
        jdbcTemplate = resolveJdbcTemplate(applicationContext);

        CaseExecutionContext.bind(caseContext);
        caseBound = true;
        try {
            routingDataSource = beginCaseDatabase(applicationContext);
            Throwable staleResourceFailure = clearTransactionResources(applicationContext, null);
            if (staleResourceFailure != null) {
                log.warn("[SmartTest] Failed to clean stale transaction resources before case [{}]",
                        caseContext.getCaseName(), staleResourceFailure);
            }
            prepareCaseData();

            ThreadScope.resetCurrentThread();
            prewarmThreadScopedMocks(applicationContext);
            SmartMockInjector.injectSmartMocks(testInstance, applicationContext);

            staticMockContext = new StaticMockContext(applicationContext);
            lifecycle.configureStaticMocks(caseContext, staticMockContext);
            warnIfMultipleContexts(testInstance.getClass());
        } catch (Throwable failure) {
            warnFailure(extensionContext, failure);
            Throwable cleanupFailure = cleanupCaseResources();
            if (cleanupFailure != null) {
                failure.addSuppressed(cleanupFailure);
            }
            throw asException(failure);
        }
    }

    @Override
    public void interceptTestTemplateMethod(Invocation<Void> invocation,
                                            ReflectiveInvocationContext<Method> invocationContext,
                                            ExtensionContext extensionContext) throws Throwable {
        try {
            invokeBeforeCaseMethods(extensionContext, caseContext.getCaseName());
            lifecycle.beforeExecute(caseContext);

            try {
                invocation.proceed();
            } catch (Throwable failure) {
                caseContext.setException(failure);
                if (!ExceptionVerifier.hasExpectException(caseContext.getCasePath())) {
                    exceptionVerifiedByLifecycle = lifecycle.verifyException(caseContext);
                    exceptionVerificationEvaluated = true;
                    if (!exceptionVerifiedByLifecycle) {
                        throw failure;
                    }
                }
                log.debug("[SmartTest] Exception captured for case [{}]: {}",
                        caseContext.getCaseName(), failure.getClass().getSimpleName());
            }

            lifecycle.afterExecute(caseContext);
            verifyCase();
            log.debug("[SmartTest] Verification passed for case: {}", caseContext.getCaseName());
        } catch (Throwable failure) {
            warnFailure(extensionContext, failure);
            throw failure;
        }
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        extensionContext.getExecutionException()
                .ifPresent(failure -> warnFailure(extensionContext, failure));
        Throwable cleanupFailure = cleanupCaseResources();
        if (cleanupFailure == null) {
            return;
        }
        if (extensionContext.getExecutionException().isPresent()) {
            extensionContext.getExecutionException().get().addSuppressed(cleanupFailure);
            return;
        }
        throw asException(cleanupFailure);
    }

    private void prepareCaseData() {
        if (jdbcTemplate == null) {
            return;
        }
        SchemaInitializer.initialize(jdbcTemplate, SCHEMA_LOCATION);
        DataSetLoader.load(jdbcTemplate, caseContext.getCasePath());
        log.debug("[SmartTest] Data prepared for case: {}", caseContext.getCaseName());
    }

    private void verifyCase() {
        List<String> failures = new ArrayList<>();
        String casePath = caseContext.getCasePath();

        boolean exceptionHandled = exceptionVerificationEvaluated
                ? exceptionVerifiedByLifecycle
                : lifecycle.verifyException(caseContext);
        if (!exceptionHandled) {
            if (caseContext.getException() != null) {
                failures.addAll(ExceptionVerifier.verify(caseContext.getException(), casePath));
            } else if (ExceptionVerifier.hasExpectException(casePath)) {
                failures.add("[exception]: expected exception but none was thrown");
            }
        }
        if (!lifecycle.verifyResult(caseContext)) {
            failures.addAll(ResultVerifier.verify(caseContext.getResult(), casePath));
        }
        if (!lifecycle.verifyDatabase(caseContext, jdbcTemplate) && jdbcTemplate != null) {
            failures.addAll(DataSetVerifier.verify(jdbcTemplate, casePath));
        }
        if (!failures.isEmpty()) {
            StringBuilder message = new StringBuilder("[SmartTest] Verification failed for case [")
                    .append(caseContext.getCaseName()).append("]:\n");
            for (String failure : failures) {
                message.append("  - ").append(failure).append("\n");
            }
            throw new AssertionError(message.toString());
        }
    }

    private Throwable cleanupCaseResources() {
        if (!caseBound) {
            return null;
        }
        Throwable failure = null;
        failure = closeStaticMocks(failure);
        failure = clearThreadScope(failure);
        failure = clearTransactionResources(applicationContext, failure);
        failure = releaseCaseDatabase(failure);
        try {
            CaseExecutionContext.clear();
        } finally {
            applicationContext = null;
            caseBound = false;
        }
        return failure;
    }

    private Throwable closeStaticMocks(Throwable failure) {
        try {
            if (staticMockContext != null) {
                staticMockContext.close();
            }
        } catch (Throwable cleanupFailure) {
            failure = appendFailure(failure, cleanupFailure);
        } finally {
            staticMockContext = null;
        }
        return failure;
    }

    private Throwable clearThreadScope(Throwable failure) {
        try {
            ThreadScope.clearCurrentThread();
        } catch (Throwable cleanupFailure) {
            failure = appendFailure(failure, cleanupFailure);
        }
        return failure;
    }

    private Throwable clearTransactionResources(ApplicationContext context, Throwable failure) {
        if (context == null) {
            return failure;
        }
        failure = clearMyBatisResources(context, failure);
        return clearConnectionResources(context, failure);
    }

    private Throwable clearMyBatisResources(ApplicationContext context, Throwable failure) {
        Class<?> sqlSessionFactoryType = loadOptionalClass(SQL_SESSION_FACTORY_CLASS, context);
        if (sqlSessionFactoryType == null) {
            return failure;
        }

        Map<String, ?> factories;
        try {
            factories = context.getBeansOfType(sqlSessionFactoryType);
        } catch (Throwable cleanupFailure) {
            return appendCleanupFailure(failure, "MyBatis SqlSessionFactory resources", cleanupFailure);
        }

        for (Map.Entry<String, ?> entry : factories.entrySet()) {
            try {
                Object resource = TransactionSynchronizationManager
                        .unbindResourceIfPossible(entry.getValue());
                closeSqlSessionResource(resource, context);
            } catch (Throwable cleanupFailure) {
                failure = appendCleanupFailure(
                        failure, "MyBatis resource for bean '" + entry.getKey() + "'", cleanupFailure);
            }
        }
        return failure;
    }

    private Throwable clearConnectionResources(ApplicationContext context, Throwable failure) {
        Map<String, DataSource> dataSources;
        try {
            dataSources = context.getBeansOfType(DataSource.class);
        } catch (Throwable cleanupFailure) {
            return appendCleanupFailure(failure, "JDBC ConnectionHolder resources", cleanupFailure);
        }

        for (Map.Entry<String, DataSource> entry : dataSources.entrySet()) {
            try {
                Object resource = TransactionSynchronizationManager
                        .unbindResourceIfPossible(entry.getValue());
                if (resource instanceof ConnectionHolder) {
                    ConnectionHolder connectionHolder = (ConnectionHolder) resource;
                    Connection connection = connectionHolder.getConnection();
                    if (connection != null) {
                        connection.close();
                    }
                }
            } catch (Throwable cleanupFailure) {
                failure = appendCleanupFailure(
                        failure, "JDBC resource for bean '" + entry.getKey() + "'", cleanupFailure);
            }
        }
        return failure;
    }

    private void closeSqlSessionResource(Object resource, ApplicationContext context) throws Exception {
        if (resource == null) {
            return;
        }

        Class<?> sqlSessionType = loadOptionalClass(SQL_SESSION_CLASS, context);
        Object sqlSession = sqlSessionType != null && sqlSessionType.isInstance(resource)
                ? resource
                : null;
        if (sqlSession == null) {
            Class<?> sqlSessionHolderType = loadOptionalClass(SQL_SESSION_HOLDER_CLASS, context);
            if (sqlSessionHolderType != null && sqlSessionHolderType.isInstance(resource)) {
                Method getSqlSession = sqlSessionHolderType.getMethod("getSqlSession");
                sqlSession = getSqlSession.invoke(resource);
            }
        }
        if (sqlSession == null) {
            return;
        }
        if (sqlSessionType != null && sqlSessionType.isInstance(sqlSession)) {
            sqlSessionType.getMethod("close").invoke(sqlSession);
        } else if (sqlSession instanceof AutoCloseable) {
            ((AutoCloseable) sqlSession).close();
        }
    }

    private Class<?> loadOptionalClass(String className, ApplicationContext context) {
        ClassLoader classLoader = context.getClassLoader();
        if (classLoader == null) {
            classLoader = Thread.currentThread().getContextClassLoader();
        }
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException ignored) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException ignoredAgain) {
                return null;
            } catch (LinkageError ignoredAgain) {
                return null;
            }
        } catch (LinkageError ignored) {
            return null;
        }
    }

    private Throwable appendCleanupFailure(Throwable failure, String resourceDescription,
                                            Throwable cleanupFailure) {
        log.warn("[SmartTest] Failed to clean {}: {}", resourceDescription,
                cleanupFailure.getMessage(), cleanupFailure);
        return appendFailure(failure, cleanupFailure);
    }

    private Throwable releaseCaseDatabase(Throwable failure) {
        try {
            if (routingDataSource != null) {
                routingDataSource.releaseCurrentCase();
            }
        } catch (Throwable cleanupFailure) {
            failure = appendFailure(failure, cleanupFailure);
        } finally {
            routingDataSource = null;
        }
        return failure;
    }

    private Throwable appendFailure(Throwable failure, Throwable additional) {
        if (failure == null) {
            return additional;
        }
        failure.addSuppressed(additional);
        return failure;
    }

    private void warnIfMultipleContexts(Class<?> testClass) {
        if (CONTEXT_DIAGNOSTICS.markRiskWarning()) {
            log.warn("[SmartTest] Multiple Spring ApplicationContexts detected for {}. "
                            + "If application code uses a static ContextHolder, factory, or registry, "
                            + "ensure configureStaticMocks() covers the relevant static gateway.",
                    testClass.getName());
        }
    }

    private void warnFailure(ExtensionContext extensionContext, Throwable failure) {
        Class<?> testClass = extensionContext.getRequiredTestClass();
        if (CONTEXT_DIAGNOSTICS.markFailureWarningFor(testClass)) {
            log.warn("[SmartTest] Case [{}] failed with {} while multiple Spring ApplicationContexts "
                            + "were observed. Check JVM static ContextHolder/factory/registry state and "
                            + "ensure configureStaticMocks() covers the relevant gateway.",
                    caseContext.getCaseName(), failure.getClass().getName());
        }
    }

    private SmartTestRoutingDataSource beginCaseDatabase(ApplicationContext context) {
        try {
            SmartTestRoutingDataSource dataSource = context.getBean(SmartTestRoutingDataSource.class);
            dataSource.beginCase();
            return dataSource;
        } catch (NoSuchBeanDefinitionException ignored) {
            return null;
        }
    }

    /** 从 ApplicationContext 获取唯一 JdbcTemplate；没有候选时跳过数据操作。 */
    static JdbcTemplate resolveJdbcTemplate(ApplicationContext applicationContext) {
        try {
            return applicationContext.getBean(JdbcTemplate.class);
        } catch (NoUniqueBeanDefinitionException e) {
            throw e;
        } catch (NoSuchBeanDefinitionException e) {
            log.warn("[SmartTest] JdbcTemplate not found, skipping data operations");
            return null;
        }
    }

    private void prewarmThreadScopedMocks(ApplicationContext context) {
        ThreadScopedMockRegistry registry;
        try {
            registry = context.getBean(ThreadScopedMockRegistry.class);
        } catch (NoSuchBeanDefinitionException e) {
            return;
        }
        for (String beanName : registry.getBeanNames()) {
            try {
                Object bean = context.getBean(beanName);
                if (bean instanceof Advised) {
                    ((Advised) bean).getTargetSource().getTarget();
                }
            } catch (Exception e) {
                log.warn("[SmartTest] Failed to prewarm thread-scoped mock '{}': {}", beanName, e.getMessage());
            }
        }
    }

    private void invokeBeforeCaseMethods(ExtensionContext extensionContext, String caseName) {
        Object testInstance = extensionContext.getRequiredTestInstance();
        for (Class<?> type = testInstance.getClass(); type != null && type != Object.class;
             type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                BeforeCase annotation = method.getAnnotation(BeforeCase.class);
                if (annotation != null && annotation.value().equals(caseName)) {
                    invokeBeforeCaseMethod(testInstance, method, caseName);
                }
            }
        }
    }

    private void invokeBeforeCaseMethod(Object testInstance, Method method, String caseName) {
        try {
            method.setAccessible(true);
            method.invoke(testInstance);
            log.debug("[SmartTest] Invoked @BeforeCase(\"{}\") -> {}", caseName, method.getName());
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException(String.format(
                    "[SmartTest] @BeforeCase(\"%s\") method %s failed: %s",
                    caseName, method.getName(), cause.getMessage()), cause);
        }
    }

    private Exception asException(Throwable failure) {
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        if (failure instanceof Exception) {
            return (Exception) failure;
        }
        return new RuntimeException(failure);
    }
}

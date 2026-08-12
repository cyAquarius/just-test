package com.just.test.smarttest.lifecycle;

import com.just.test.smarttest.annotation.BeforeCase;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.context.CaseExecutionContext;
import com.just.test.smarttest.datasource.SmartTestRoutingDataSource;
import com.just.test.smarttest.datasource.SchemaInitializer;
import com.just.test.smarttest.loader.DataSetLoader;
import com.just.test.smarttest.mock.SmartMockTestExecutionListener;
import com.just.test.smarttest.mock.ThreadScopedMockRegistry;
import com.just.test.smarttest.scope.ThreadScope;
import com.just.test.smarttest.verifier.DataSetVerifier;
import com.just.test.smarttest.verifier.ExceptionVerifier;
import com.just.test.smarttest.verifier.ResultVerifier;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据驱动模式的完整生命周期管理。
 *
 * <p>测试实例必须实现 {@link SmartTestLifecycle}。</p>
 *
 * <p>执行顺序：clean → prepare → @BeforeCase → beforeExecute → proceed
 * → afterExecute → verify(exception/result/db) → clean</p>
 */
public class SmartTestExtension implements InvocationInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SmartTestExtension.class);
    private static final String SCHEMA_LOCATION = "classpath:sql/schema.sql";

    @Override
    public void interceptTestTemplateMethod(Invocation<Void> invocation,
                                            ReflectiveInvocationContext<Method> invocationContext,
                                            ExtensionContext extensionContext) throws Throwable {
        CaseContext ctx = extractCaseContext(invocationContext);
        if (ctx == null) {
            invocation.proceed();
            return;
        }

        Object testInstance = extensionContext.getRequiredTestInstance();
        if (!(testInstance instanceof SmartTestLifecycle)) {
            throw new IllegalStateException(String.format(
                    "[SmartTest] %s must implement SmartTestLifecycle",
                    testInstance.getClass().getName()));
        }
        SmartTestLifecycle lifecycle = (SmartTestLifecycle) testInstance;
        JdbcTemplate jdbcTemplate = getJdbcTemplate(extensionContext);
        String casePath = ctx.getCasePath();

        ApplicationContext appCtx = SpringExtension.getApplicationContext(extensionContext);
        SmartTestRoutingDataSource routingDataSource = beginCaseDatabase(appCtx);
        Throwable testFailure = null;
        try {
            CaseExecutionContext.bind(ctx);
            // 1. clean
            if (jdbcTemplate != null) {
                SchemaInitializer.initialize(jdbcTemplate, SCHEMA_LOCATION);
                DataSetLoader.cleanTables(jdbcTemplate);
                // 2. prepare
                DataSetLoader.load(jdbcTemplate, casePath);
                log.debug("[SmartTest] Data prepared for case: {}", ctx.getCaseName());
            }

            // 3. reset thread-scoped mocks（每个 case 拿到全新 mock 实例）
            ThreadScope.resetCurrentThread();

            // 3.5 预热所有 thread-scoped mock，避免 ScopedProxy 懒加载与 Mockito matcher 时序冲突
            //    （@Autowired 注入的 ScopedProxy 在测试里首次 when(proxy.method(anyMatcher())) 时
            //     会触发 Mockito.mock() 懒加载，此时 matcher 栈已非空，抛 InvalidUseOfMatchersException）
            prewarmThreadScopedMocks(appCtx);

            // 4. 重新注入 @SmartMock 字段为实际的 thread-local mock（非 proxy）
            SmartMockTestExecutionListener.injectSmartMocks(testInstance, appCtx);

            // 5. @BeforeCase
            invokeBeforeCaseMethods(extensionContext, ctx.getCaseName());

            // 6. beforeExecute
            lifecycle.beforeExecute(ctx);

            // 7. proceed
            try {
                invocation.proceed();
            } catch (Throwable t) {
                ctx.setException(t);
                if (!ExceptionVerifier.hasExpectException(casePath)) {
                    throw t;
                }
                log.debug("[SmartTest] Exception captured for case [{}]: {}",
                        ctx.getCaseName(), t.getClass().getSimpleName());
            }

            // 8. afterExecute
            lifecycle.afterExecute(ctx);

            // 9. verify
            List<String> failures = new ArrayList<>();

            if (!lifecycle.verifyException(ctx)) {
                if (ctx.getException() != null) {
                    failures.addAll(ExceptionVerifier.verify(ctx.getException(), casePath));
                } else if (ExceptionVerifier.hasExpectException(casePath)) {
                    failures.add("[exception]: expected exception but none was thrown");
                }
            }

            if (!lifecycle.verifyResult(ctx)) {
                failures.addAll(ResultVerifier.verify(ctx.getResult(), casePath));
            }

            if (!lifecycle.verifyDatabase(ctx, jdbcTemplate)) {
                if (jdbcTemplate != null) {
                    failures.addAll(DataSetVerifier.verify(jdbcTemplate, casePath));
                }
            }

            if (!failures.isEmpty()) {
                StringBuilder sb = new StringBuilder("[SmartTest] Verification failed for case [")
                        .append(ctx.getCaseName()).append("]:\n");
                for (String f : failures) {
                    sb.append("  - ").append(f).append("\n");
                }
                throw new AssertionError(sb.toString());
            }

            log.debug("[SmartTest] Verification passed for case: {}", ctx.getCaseName());

        } catch (Throwable t) {
            testFailure = t;
            throw t;
        } finally {
            Throwable cleanupFailure = null;
            try {
                ThreadScope.clearCurrentThread();
            } catch (Throwable t) {
                cleanupFailure = t;
            }
            try {
                if (routingDataSource != null) {
                    routingDataSource.releaseCurrentCase();
                }
            } catch (Throwable t) {
                if (cleanupFailure == null) {
                    cleanupFailure = t;
                } else {
                    cleanupFailure.addSuppressed(t);
                }
            } finally {
                CaseExecutionContext.clear();
            }
            if (cleanupFailure != null) {
                if (testFailure != null) {
                    testFailure.addSuppressed(cleanupFailure);
                } else {
                    throw cleanupFailure;
                }
            }
        }
    }

    /**
     * 预热所有 thread-scoped mock：对 Registry 里记录的每个 bean，触发 ScopedProxy 的 target 创建。
     *
     * <p>这样当测试代码执行 {@code when(proxy.method(anyMatcher()))} 时，target 已经存在，
     * proxy 不再触发懒加载 {@code Mockito.mock()}，避免与 matcher 栈冲突。</p>
     */
    private void prewarmThreadScopedMocks(ApplicationContext ctx) {
        ThreadScopedMockRegistry registry;
        try {
            registry = ctx.getBean(ThreadScopedMockRegistry.class);
        } catch (NoSuchBeanDefinitionException e) {
            return;  // 无 thread-scoped mock，跳过
        }
        for (String beanName : registry.getBeanNames()) {
            try {
                Object bean = ctx.getBean(beanName);
                if (bean instanceof Advised) {
                    ((Advised) bean).getTargetSource().getTarget();
                }
            } catch (Exception e) {
                log.warn("[SmartTest] Failed to prewarm thread-scoped mock '{}': {}", beanName, e.getMessage());
            }
        }
    }

    private CaseContext extractCaseContext(ReflectiveInvocationContext<Method> invocationContext) {
        for (Object arg : invocationContext.getArguments()) {
            if (arg instanceof CaseContext) {
                return (CaseContext) arg;
            }
        }
        return null;
    }

    private JdbcTemplate getJdbcTemplate(ExtensionContext extensionContext) {
        return resolveJdbcTemplate(SpringExtension.getApplicationContext(extensionContext));
    }

    private SmartTestRoutingDataSource beginCaseDatabase(ApplicationContext context) {
        try {
            SmartTestRoutingDataSource dataSource = context.getBean(SmartTestRoutingDataSource.class);
            dataSource.beginCase();
            return dataSource;
        } catch (NoSuchBeanDefinitionException ignored) {
            // 使用外部数据源时由外部生命周期管理。
            return null;
        }
    }

    /**
     * 从 ApplicationContext 获取 JdbcTemplate，找不到返回 null。
     * 供 SmartTestExecutionListener 等同包类复用。
     */
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

    private void invokeBeforeCaseMethods(ExtensionContext extensionContext, String caseName) {
        Object testInstance = extensionContext.getRequiredTestInstance();
        for (Class<?> clazz = testInstance.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (Method method : clazz.getDeclaredMethods()) {
                BeforeCase annotation = method.getAnnotation(BeforeCase.class);
                if (annotation != null && annotation.value().equals(caseName)) {
                    try {
                        method.setAccessible(true);
                        method.invoke(testInstance);
                        log.debug("[SmartTest] Invoked @BeforeCase(\"{}\") -> {}", caseName, method.getName());
                    } catch (Exception e) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        throw new RuntimeException(
                                String.format("[SmartTest] @BeforeCase(\"%s\") method %s failed: %s",
                                        caseName, method.getName(), cause.getMessage()), cause);
                    }
                }
            }
        }
    }
}

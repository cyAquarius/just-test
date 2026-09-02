package com.just.test.smarttest.internal.lifecycle;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.lifecycle.SmartTestLifecycle;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.platform.commons.annotation.Testable;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@code @SmartTest} 的共享类级校验：{@link SmartTestLifecycle}、JUnit 可执行方法、
 * {@code @TestInstance(PER_CLASS)} 与类内并发，以及 Spring {@code @Transactional}/{@code @Sql}。
 *
 * <p>不对消费方提供兼容承诺。Boot 模块的公开 {@code SmartTestClassValidationExtension}
 * 在 BeforeAll 调用本校验，并另行检查 Boot bootstrapper 注解。</p>
 */
public final class SmartTestClassValidator {

    /**
     * JUnit 是否启用并行。未启用时 {@code @Execution} 与 {@code mode.default} 不会产生类内并发。
     */
    static final String PARALLEL_ENABLED_PROPERTY =
            "junit.jupiter.execution.parallel.enabled";

    /**
     * JUnit 方法级默认并行模式。类级 {@code ExtensionContext#getExecutionMode()} 对应
     * {@code mode.classes.default}，不能用来判断同一类中的 case 是否并发。
     */
    static final String PARALLEL_MODE_DEFAULT_PROPERTY =
            "junit.jupiter.execution.parallel.mode.default";

    private SmartTestClassValidator() {
    }

    public static void validate(Class<?> testClass) {
        validate(testClass, null);
    }

    public static void validate(Class<?> testClass, ExtensionContext context) {
        requireSmartTestLifecycle(testClass);
        validateClassSpringLifecycleAnnotations(testClass);
        List<Method> unsupportedMethods = AnnotationSupport.findAnnotatedMethods(
                        testClass, Testable.class, HierarchyTraversalMode.TOP_DOWN)
                .stream()
                .filter(method -> !AnnotationSupport.isAnnotated(method, CaseSource.class))
                .collect(Collectors.toList());
        if (!unsupportedMethods.isEmpty()) {
            String methods = unsupportedMethods.stream()
                    .map(Method::getName)
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new ExtensionConfigurationException(String.format(
                    "[SmartTest] %s contains unsupported JUnit test methods: %s. "
                            + "A @SmartTest class may only contain @CaseSource test methods; "
                            + "move ordinary JUnit tests to a separate test class.",
                    testClass.getName(), methods));
        }
        validateCaseSourceMethods(testClass);
        validatePerClassConcurrentExecution(testClass, context);
    }

    public static ExtensionConfigurationException configurationError(Class<?> testClass, String detail) {
        return new ExtensionConfigurationException(
                String.format("[SmartTest] %s %s.", testClass.getName(), detail));
    }

    private static void requireSmartTestLifecycle(Class<?> testClass) {
        if (!SmartTestLifecycle.class.isAssignableFrom(testClass)) {
            throw configurationError(testClass, "must implement SmartTestLifecycle");
        }
    }

    private static void validateClassSpringLifecycleAnnotations(Class<?> testClass) {
        if (AnnotatedElementUtils.hasAnnotation(testClass, Transactional.class)
                || AnnotatedElementUtils.hasAnnotation(testClass, Sql.class)) {
            throw configurationError(testClass,
                    "does not support class-level @Transactional or @Sql because they run before a case is bound");
        }
    }

    private static void validateCaseSourceMethods(Class<?> testClass) {
        List<Method> caseMethods = AnnotationSupport.findAnnotatedMethods(
                testClass, CaseSource.class, HierarchyTraversalMode.TOP_DOWN);
        for (Method method : caseMethods) {
            List<String> executableAnnotations = Arrays.stream(method.getAnnotations())
                    .filter(annotation -> AnnotationSupport.isAnnotated(
                            annotation.annotationType(), Testable.class))
                    .map(annotation -> "@" + annotation.annotationType().getSimpleName())
                    .sorted()
                    .collect(Collectors.toList());
            if (executableAnnotations.size() > 1) {
                throw configurationError(testClass, String.format(
                        "@CaseSource method %s declares competing JUnit test annotations: %s. "
                                + "@CaseSource must be the only executable test annotation on the method",
                        method.getName(), String.join(", ", executableAnnotations)));
            }
            if (AnnotatedElementUtils.hasAnnotation(method, Transactional.class)
                    || AnnotatedElementUtils.hasAnnotation(method, Sql.class)) {
                throw configurationError(testClass, String.format(
                        "@CaseSource method %s must not use @Transactional or @Sql because they run before a case is bound",
                        method.getName()));
            }
        }
    }

    private static void validatePerClassConcurrentExecution(Class<?> testClass, ExtensionContext context) {
        if (resolveLifecycle(testClass, context) != TestInstance.Lifecycle.PER_CLASS) {
            return;
        }
        if (!hasConcurrentCaseExecution(testClass, context)) {
            return;
        }
        throw configurationError(testClass,
                "must not combine @TestInstance(PER_CLASS) with concurrent case execution. "
                        + "Concurrent @CaseSource invocations share one test instance and can race @SmartMock field injection. "
                        + "Use the default PER_METHOD lifecycle, or keep PER_CLASS with @Execution(SAME_THREAD)");
    }

    private static TestInstance.Lifecycle resolveLifecycle(Class<?> testClass, ExtensionContext context) {
        if (context != null) {
            Optional<TestInstance.Lifecycle> resolved = context.getTestInstanceLifecycle();
            if (resolved.isPresent()) {
                return resolved.get();
            }
        }
        return AnnotationSupport.findAnnotation(testClass, TestInstance.class)
                .map(TestInstance::value)
                .orElse(TestInstance.Lifecycle.PER_METHOD);
    }

    /**
     * 类内 case 是否会并发执行。
     *
     * <p>有 {@code ExtensionContext} 时，先看 {@code junit.jupiter.execution.parallel.enabled}：
     * JUnit 在未启用并行时会忽略 {@code @Execution} 和 {@code mode.default}，因此
     * {@code enabled=false} 加上残留的 {@code mode.default=concurrent} 不算并发。</p>
     *
     * <p>没有 context 时无法读取平台配置，显式 {@code @Execution(CONCURRENT)} 视为会并发。
     * 方法上的 {@code @Execution} 优先，否则继承类上的 {@code @Execution}，再否则使用
     * {@code mode.default}（缺省 {@code SAME_THREAD}）。不读取
     * {@code ExtensionContext#getExecutionMode()}，以免把类间并行误判为类内并发。</p>
     */
    private static boolean hasConcurrentCaseExecution(Class<?> testClass, ExtensionContext context) {
        if (context != null && !isParallelExecutionEnabled(context)) {
            return false;
        }
        ExecutionMode defaultMode = resolveDefaultMethodExecutionMode(testClass, context);
        List<Method> caseMethods = AnnotationSupport.findAnnotatedMethods(
                testClass, CaseSource.class, HierarchyTraversalMode.TOP_DOWN);
        if (caseMethods.isEmpty()) {
            return defaultMode == ExecutionMode.CONCURRENT;
        }
        for (Method method : caseMethods) {
            ExecutionMode methodMode = AnnotationSupport.findAnnotation(method, Execution.class)
                    .map(Execution::value)
                    .orElse(defaultMode);
            if (methodMode == ExecutionMode.CONCURRENT) {
                return true;
            }
        }
        return false;
    }

    private static boolean isParallelExecutionEnabled(ExtensionContext context) {
        return configurationParameter(context, PARALLEL_ENABLED_PROPERTY)
                .map(value -> Boolean.parseBoolean(value.trim()))
                .orElse(false);
    }

    private static ExecutionMode resolveDefaultMethodExecutionMode(Class<?> testClass, ExtensionContext context) {
        Optional<Execution> classExecution = AnnotationSupport.findAnnotation(testClass, Execution.class);
        if (classExecution.isPresent()) {
            return classExecution.get().value();
        }
        if (context != null) {
            return configurationParameter(context, PARALLEL_MODE_DEFAULT_PROPERTY)
                    .map(SmartTestClassValidator::parseExecutionMode)
                    .orElse(ExecutionMode.SAME_THREAD);
        }
        return ExecutionMode.SAME_THREAD;
    }

    private static Optional<String> configurationParameter(ExtensionContext context, String key) {
        Optional<String> value = context.getConfigurationParameter(key);
        return value == null ? Optional.empty() : value;
    }

    private static ExecutionMode parseExecutionMode(String value) {
        try {
            return ExecutionMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ignored) {
            return ExecutionMode.SAME_THREAD;
        }
    }
}

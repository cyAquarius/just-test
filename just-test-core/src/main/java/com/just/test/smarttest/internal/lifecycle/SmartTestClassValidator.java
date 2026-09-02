package com.just.test.smarttest.internal.lifecycle;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.lifecycle.SmartTestLifecycle;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.platform.commons.annotation.Testable;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code @SmartTest} 的共享类级校验：{@link SmartTestLifecycle}、JUnit 可执行方法与
 * Spring {@code @Transactional}/{@code @Sql}。
 *
 * <p>不对消费方提供兼容承诺。Boot 模块的公开 {@code SmartTestClassValidationExtension}
 * 在 BeforeAll 调用本校验，并另行检查 Boot bootstrapper 注解。</p>
 */
public final class SmartTestClassValidator {

    private SmartTestClassValidator() {
    }

    public static void validate(Class<?> testClass) {
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
}

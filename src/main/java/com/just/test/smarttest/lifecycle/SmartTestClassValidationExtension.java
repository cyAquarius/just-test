package com.just.test.smarttest.lifecycle;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.annotation.Testable;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** 校验 {@code @SmartTest} 测试类只使用 SmartTest case 执行模型。 */
public final class SmartTestClassValidationExtension implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        validateTestClass(context.getRequiredTestClass());
    }

    static void validateTestClass(Class<?> testClass) {
        validateClassAnnotations(testClass);
        List<Method> unsupportedMethods = AnnotationSupport.findAnnotatedMethods(
                        testClass, Testable.class, HierarchyTraversalMode.TOP_DOWN)
                .stream()
                .filter(method -> !AnnotationSupport.isAnnotated(method, CaseSource.class))
                .collect(Collectors.toList());
        if (unsupportedMethods.isEmpty()) {
            validateCaseSourceMethods(testClass);
            return;
        }

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

    private static void validateClassAnnotations(Class<?> testClass) {
        if (AnnotatedElementUtils.hasAnnotation(testClass, SpringBootTest.class)) {
            throw configurationError(testClass,
                    "must not combine @SmartTest with @SpringBootTest; @SmartTest already configures the Boot bootstrapper");
        }
        if (hasAdditionalBootstrapWith(testClass)) {
            throw configurationError(testClass,
                    "must not declare another @BootstrapWith; @SmartTest already configures the Boot bootstrapper");
        }
        if (AnnotatedElementUtils.hasAnnotation(testClass, Transactional.class)
                || AnnotatedElementUtils.hasAnnotation(testClass, Sql.class)) {
            throw configurationError(testClass,
                    "does not support class-level @Transactional or @Sql because they run before a case is bound");
        }
    }

    private static boolean hasAdditionalBootstrapWith(Class<?> testClass) {
        for (Annotation annotation : testClass.getAnnotations()) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (annotationType != SmartTest.class
                    && (annotationType == BootstrapWith.class
                    || AnnotatedElementUtils.hasAnnotation(annotationType, BootstrapWith.class))) {
                return true;
            }
        }
        return false;
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

    private static ExtensionConfigurationException configurationError(Class<?> testClass, String detail) {
        return new ExtensionConfigurationException(
                String.format("[SmartTest] %s %s.", testClass.getName(), detail));
    }
}

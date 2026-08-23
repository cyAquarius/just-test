package com.just.test.smarttest.lifecycle;

import com.just.test.smarttest.annotation.CaseSource;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.annotation.Testable;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.support.HierarchyTraversalMode;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

/** 校验 {@code @SmartTest} 测试类只使用 SmartTest case 执行模型。 */
public final class SmartTestClassValidationExtension implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        validateTestClass(context.getRequiredTestClass());
    }

    static void validateTestClass(Class<?> testClass) {
        List<Method> unsupportedMethods = AnnotationSupport.findAnnotatedMethods(
                        testClass, Testable.class, HierarchyTraversalMode.TOP_DOWN)
                .stream()
                .filter(method -> !AnnotationSupport.isAnnotated(method, CaseSource.class))
                .collect(Collectors.toList());
        if (unsupportedMethods.isEmpty()) {
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
}

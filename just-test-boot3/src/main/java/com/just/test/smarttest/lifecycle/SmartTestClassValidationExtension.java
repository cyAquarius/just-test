package com.just.test.smarttest.lifecycle;

import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.internal.lifecycle.SmartTestClassValidator;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.BootstrapWith;

import java.lang.annotation.Annotation;

/**
 * {@code @SmartTest} 元注解注册的类级校验扩展。
 *
 * <p>消费测试只需声明 {@code @SmartTest}，不要直接 {@code @ExtendWith} 本类型。
 * 本扩展必须保持 public，以便 JUnit 能从注解实例化它。</p>
 *
 * <p>Boot bootstrapper 约束留在本适配器；JUnit / {@code SmartTestLifecycle} /
 * {@code PER_CLASS} 并发 / {@code @Transactional} / {@code @Sql} 共享规则委托给 core 的
 * {@link SmartTestClassValidator}，在 {@code BeforeAll} 失败。</p>
 */
public final class SmartTestClassValidationExtension implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();
        validateBootBootstrapper(testClass);
        SmartTestClassValidator.validate(testClass, context);
    }

    static void validateTestClass(Class<?> testClass) {
        validateBootBootstrapper(testClass);
        SmartTestClassValidator.validate(testClass);
    }

    private static void validateBootBootstrapper(Class<?> testClass) {
        if (AnnotatedElementUtils.hasAnnotation(testClass, SpringBootTest.class)) {
            throw SmartTestClassValidator.configurationError(testClass,
                    "must not combine @SmartTest with @SpringBootTest; @SmartTest already configures the Boot bootstrapper");
        }
        if (hasAdditionalBootstrapWith(testClass)) {
            throw SmartTestClassValidator.configurationError(testClass,
                    "must not declare another @BootstrapWith; @SmartTest already configures the Boot bootstrapper");
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
}

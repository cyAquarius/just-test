package com.just.test.lifecycle;

import com.just.test.annotation.JustTest;
import com.just.test.internal.lifecycle.JustTestClassValidator;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.BootstrapWith;

import java.lang.annotation.Annotation;

/**
 * {@code @JustTest} 元注解注册的类级校验扩展。
 *
 * <p>消费测试只需声明 {@code @JustTest}，不要直接 {@code @ExtendWith} 本类型。
 * 本扩展必须保持 public，以便 JUnit 能从注解实例化它。</p>
 *
 * <p>Boot bootstrapper 约束留在本适配器；JUnit / {@code JustTestLifecycle} /
 * {@code PER_CLASS} 并发 / {@code @Transactional} / {@code @Sql} 共享规则委托给 core 的
 * {@link JustTestClassValidator}，在 {@code BeforeAll} 失败。</p>
 */
public final class JustTestClassValidationExtension implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();
        validateBootBootstrapper(testClass);
        JustTestClassValidator.validate(testClass, context);
    }

    static void validateTestClass(Class<?> testClass) {
        validateBootBootstrapper(testClass);
        JustTestClassValidator.validate(testClass);
    }

    private static void validateBootBootstrapper(Class<?> testClass) {
        if (AnnotatedElementUtils.hasAnnotation(testClass, SpringBootTest.class)) {
            throw JustTestClassValidator.configurationError(testClass,
                    "must not combine @JustTest with @SpringBootTest; @JustTest already configures the Boot bootstrapper");
        }
        if (hasAdditionalBootstrapWith(testClass)) {
            throw JustTestClassValidator.configurationError(testClass,
                    "must not declare another @BootstrapWith; @JustTest already configures the Boot bootstrapper");
        }
    }

    private static boolean hasAdditionalBootstrapWith(Class<?> testClass) {
        for (Annotation annotation : testClass.getAnnotations()) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (annotationType != JustTest.class
                    && (annotationType == BootstrapWith.class
                    || AnnotatedElementUtils.hasAnnotation(annotationType, BootstrapWith.class))) {
                return true;
            }
        }
        return false;
    }
}

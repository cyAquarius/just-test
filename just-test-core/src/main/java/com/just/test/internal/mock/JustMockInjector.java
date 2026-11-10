package com.just.test.internal.mock;

import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustSpy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.Advised;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;

/** 将测试实例中的 {@link JustMock} / {@link JustSpy} 字段绑定到当前 case 线程的 Mockito mock。 */
public final class JustMockInjector {

    private static final Logger log = LoggerFactory.getLogger(JustMockInjector.class);

    private JustMockInjector() {
    }

    public static void injectJustMocks(Object testInstance, ApplicationContext context) {
        for (Class<?> type = testInstance.getClass(); type != null && type != Object.class;
             type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                JustMockDefinition definition = definitionFor(field);
                if (definition != null) {
                    injectMock(testInstance, field, context, definition);
                }
            }
        }
    }

    private static JustMockDefinition definitionFor(Field field) {
        JustMock justMock = field.getAnnotation(JustMock.class);
        JustSpy justSpy = field.getAnnotation(JustSpy.class);
        if (justMock != null && justSpy != null) {
            throw new IllegalStateException(String.format(
                    "[JustTest] %s.%s declares both @JustMock and @JustSpy. A field must use only one of them.",
                    field.getDeclaringClass().getName(), field.getName()));
        }
        if (justMock != null) {
            return JustMockDefinition.forField(field, justMock);
        }
        if (justSpy != null) {
            return JustMockDefinition.forSpyField(field, justSpy);
        }
        return null;
    }

    private static void injectMock(Object testInstance, Field field, ApplicationContext context,
                                   JustMockDefinition definition) {
        try {
            String beanName = context.getBean(JustMockBindings.class).getBeanName(definition);
            if (beanName == null) {
                throw new IllegalStateException("No resolved bean binding for " + definition.describe());
            }
            Object bean = context.getBean(beanName);
            if (bean instanceof Advised) {
                bean = ((Advised) bean).getTargetSource().getTarget();
            }
            field.setAccessible(true);
            field.set(testInstance, bean);
            log.debug("[{}] Injected {} into {}.{}",
                    definition.isSpy() ? "JustSpy" : "JustMock",
                    field.getType().getSimpleName(),
                    testInstance.getClass().getSimpleName(),
                    field.getName());
        } catch (Exception e) {
            throw new RuntimeException(String.format(
                    "[%s] Failed to inject %s into %s.%s: %s",
                    definition.isSpy() ? "JustSpy" : "JustMock",
                    field.getType().getName(),
                    testInstance.getClass().getName(),
                    field.getName(),
                    e.getMessage()), e);
        }
    }
}

package com.just.test.internal.mock;

import com.just.test.annotation.JustMock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.Advised;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;

/** 将测试实例中的 {@link JustMock} 字段绑定到当前 case 线程的实际 Mockito mock。 */
public final class JustMockInjector {

    private static final Logger log = LoggerFactory.getLogger(JustMockInjector.class);

    private JustMockInjector() {
    }

    public static void injectJustMocks(Object testInstance, ApplicationContext context) {
        for (Class<?> type = testInstance.getClass(); type != null && type != Object.class;
             type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isAnnotationPresent(JustMock.class)) {
                    injectMock(testInstance, field, context);
                }
            }
        }
    }

    private static void injectMock(Object testInstance, Field field, ApplicationContext context) {
        try {
            JustMock justMock = field.getAnnotation(JustMock.class);
            JustMockDefinition definition = JustMockDefinition.forField(field, justMock);
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
            log.debug("[JustMock] Injected {} into {}.{}",
                    field.getType().getSimpleName(),
                    testInstance.getClass().getSimpleName(),
                    field.getName());
        } catch (Exception e) {
            throw new RuntimeException(String.format(
                    "[JustMock] Failed to inject %s into %s.%s: %s",
                    field.getType().getName(),
                    testInstance.getClass().getName(),
                    field.getName(),
                    e.getMessage()), e);
        }
    }
}

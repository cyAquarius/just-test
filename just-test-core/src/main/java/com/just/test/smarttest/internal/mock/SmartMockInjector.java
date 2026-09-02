package com.just.test.smarttest.internal.mock;

import com.just.test.smarttest.annotation.SmartMock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.Advised;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;

/** 将测试实例中的 {@link SmartMock} 字段绑定到当前 case 线程的实际 Mockito mock。 */
public final class SmartMockInjector {

    private static final Logger log = LoggerFactory.getLogger(SmartMockInjector.class);

    private SmartMockInjector() {
    }

    public static void injectSmartMocks(Object testInstance, ApplicationContext context) {
        for (Class<?> type = testInstance.getClass(); type != null && type != Object.class;
             type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isAnnotationPresent(SmartMock.class)) {
                    injectMock(testInstance, field, context);
                }
            }
        }
    }

    private static void injectMock(Object testInstance, Field field, ApplicationContext context) {
        try {
            SmartMock smartMock = field.getAnnotation(SmartMock.class);
            SmartMockDefinition definition = SmartMockDefinition.forField(field, smartMock);
            String beanName = context.getBean(SmartMockBindings.class).getBeanName(definition);
            if (beanName == null) {
                throw new IllegalStateException("No resolved bean binding for " + definition.describe());
            }
            Object bean = context.getBean(beanName);
            if (bean instanceof Advised) {
                bean = ((Advised) bean).getTargetSource().getTarget();
            }
            field.setAccessible(true);
            field.set(testInstance, bean);
            log.debug("[SmartMock] Injected {} into {}.{}",
                    field.getType().getSimpleName(),
                    testInstance.getClass().getSimpleName(),
                    field.getName());
        } catch (Exception e) {
            throw new RuntimeException(String.format(
                    "[SmartMock] Failed to inject %s into %s.%s: %s",
                    field.getType().getName(),
                    testInstance.getClass().getName(),
                    field.getName(),
                    e.getMessage()), e);
        }
    }
}

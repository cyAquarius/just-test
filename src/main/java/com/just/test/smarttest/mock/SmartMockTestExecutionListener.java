package com.just.test.smarttest.mock;

import com.just.test.smarttest.annotation.SmartMock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.Advised;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

import java.lang.reflect.Field;

/**
 * 将 @SmartMock 字段注入为实际的 thread-local Mockito mock（非 ScopedProxy）。
 *
 * <p>ScopedProxy 仅用于 ApplicationContext 内部的 @Autowired 注入（服务层），
 * 测试代码需要直接持有 Mockito mock 才能使用 when()/doNothing().when()/verify()。</p>
 *
 * <p>通过 {@link Advised#getTargetSource()} 从 proxy 解析出当前线程的 mock 实例。</p>
 */
public class SmartMockTestExecutionListener implements TestExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(SmartMockTestExecutionListener.class);

    @Override
    public void prepareTestInstance(TestContext testContext) {
        injectSmartMocks(testContext.getTestInstance(), testContext.getApplicationContext());
    }

    /**
     * 将 @SmartMock 字段注入为实际的 thread-local Mockito mock（非 ScopedProxy）。
     * 供 SmartTestExtension 每个 case 前复用。
     */
    public static void injectSmartMocks(Object testInstance, ApplicationContext ctx) {
        for (Class<?> clazz = testInstance.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(SmartMock.class)) {
                    injectMock(testInstance, field, ctx);
                }
            }
        }
    }

    private static void injectMock(Object testInstance, Field field, ApplicationContext ctx) {
        try {
            SmartMock smartMock = field.getAnnotation(SmartMock.class);
            SmartMockDefinition definition = SmartMockDefinition.forField(field, smartMock);
            String beanName = ctx.getBean(SmartMockBindings.class).getBeanName(definition);
            if (beanName == null) {
                throw new IllegalStateException("No resolved bean binding for " + definition.describe());
            }
            Object bean = ctx.getBean(beanName);
            // 从 ScopedProxy 解析出当前线程的实际 Mockito mock
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

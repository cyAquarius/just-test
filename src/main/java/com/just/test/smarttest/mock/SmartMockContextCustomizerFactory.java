package com.just.test.smarttest.mock;

import com.just.test.smarttest.annotation.SmartMock;
import com.just.test.smarttest.annotation.SmartTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 扫描测试类（含父类）的 {@link SmartMock} 字段，收集需要 mock 的类型。
 *
 * <p>通过 {@code META-INF/spring.factories} 注册为 Spring Test SPI。</p>
 */
public class SmartMockContextCustomizerFactory implements ContextCustomizerFactory {

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass,
                                                     List<ContextConfigurationAttributes> configAttributes) {
        Set<SmartMockDefinition> definitions = new LinkedHashSet<>();
        for (Class<?> clazz = testClass; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (Field field : clazz.getDeclaredFields()) {
                SmartMock smartMock = field.getAnnotation(SmartMock.class);
                if (smartMock != null) {
                    definitions.add(SmartMockDefinition.forField(field, smartMock));
                }
            }
        }
        boolean smartTest = AnnotatedElementUtils.hasAnnotation(testClass, SmartTest.class);
        if (!smartTest) {
            if (!definitions.isEmpty()) {
                throw new IllegalStateException(String.format(
                        "[SmartTest] %s uses @SmartMock without @SmartTest. "
                                + "@SmartMock is only supported during SmartTest case invocations.",
                        testClass.getName()));
            }
            return null;
        }
        return new SmartMockContextCustomizer(definitions);
    }
}

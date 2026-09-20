package com.just.test.internal.mock;

import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustTestMarker;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 扫描测试类（含父类）的 {@link JustMock} 字段，收集需要 mock 的类型。
 *
 * <p>不对消费方提供兼容承诺。通过 {@code META-INF/spring.factories} 注册为
 * Spring Test SPI，因此必须保持 public。</p>
 */
public class JustMockContextCustomizerFactory implements ContextCustomizerFactory {

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass,
                                                     List<ContextConfigurationAttributes> configAttributes) {
        Set<JustMockDefinition> definitions = new LinkedHashSet<>();
        for (Class<?> clazz = testClass; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (Field field : clazz.getDeclaredFields()) {
                JustMock justMock = field.getAnnotation(JustMock.class);
                if (justMock != null) {
                    definitions.add(JustMockDefinition.forField(field, justMock));
                }
            }
        }
        boolean justTest = AnnotatedElementUtils.hasAnnotation(testClass, JustTestMarker.class);
        if (!justTest) {
            if (!definitions.isEmpty()) {
                throw new IllegalStateException(String.format(
                        "[JustTest] %s uses @JustMock without @JustTest. "
                                + "@JustMock is only supported during JustTest case invocations.",
                        testClass.getName()));
            }
            return null;
        }
        return new JustMockContextCustomizer(definitions);
    }
}

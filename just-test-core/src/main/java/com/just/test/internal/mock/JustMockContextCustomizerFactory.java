package com.just.test.internal.mock;

import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustSpy;
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
 * 扫描测试类（含父类）的 {@link JustMock} / {@link JustSpy} 字段。
 *
 * <p>不对消费方提供兼容承诺。通过 {@code META-INF/spring.factories} 注册为
 * Spring Test SPI，因此必须保持 public。</p>
 */
public class JustMockContextCustomizerFactory implements ContextCustomizerFactory {

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass,
                                                     List<ContextConfigurationAttributes> configAttributes) {
        Set<JustMockDefinition> mocks = new LinkedHashSet<JustMockDefinition>();
        Set<JustMockDefinition> spies = new LinkedHashSet<JustMockDefinition>();
        for (Class<?> clazz = testClass; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (Field field : clazz.getDeclaredFields()) {
                JustMock justMock = field.getAnnotation(JustMock.class);
                JustSpy justSpy = field.getAnnotation(JustSpy.class);
                if (justMock != null && justSpy != null) {
                    throw new IllegalStateException(String.format(
                            "[JustTest] %s.%s declares both @JustMock and @JustSpy. "
                                    + "A field must use only one of them.",
                            field.getDeclaringClass().getName(), field.getName()));
                }
                if (justMock != null) {
                    mocks.add(JustMockDefinition.forField(field, justMock));
                } else if (justSpy != null) {
                    spies.add(JustMockDefinition.forSpyField(field, justSpy));
                }
            }
        }
        boolean justTest = AnnotatedElementUtils.hasAnnotation(testClass, JustTestMarker.class);
        if (!justTest) {
            if (!mocks.isEmpty() || !spies.isEmpty()) {
                throw new IllegalStateException(String.format(
                        "[JustTest] %s uses %s without @JustTest. "
                                + "@JustMock and @JustSpy are only supported during JustTest case invocations.",
                        testClass.getName(), annotationNames(mocks, spies)));
            }
            return null;
        }
        return new JustMockContextCustomizer(mocks, spies);
    }

    private static String annotationNames(Set<JustMockDefinition> mocks, Set<JustMockDefinition> spies) {
        if (!mocks.isEmpty() && !spies.isEmpty()) {
            return "@JustMock and @JustSpy";
        }
        return spies.isEmpty() ? "@JustMock" : "@JustSpy";
    }
}

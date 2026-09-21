package com.just.test.internal.mock;

import com.just.test.annotation.JustMock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 一个 {@code @JustMock} 字段表达的 Bean 选择意图。
 *
 * <p>声明类只用于诊断；Context 缓存只关心会影响 Bean 选择的语义字段，
 * 使不同测试类的等价声明能够复用 Context。</p>
 */
final class JustMockDefinition {

    private final Class<?> type;
    private final String fieldName;
    private final String explicitBeanName;
    private final Set<Annotation> qualifierAnnotations;
    private final String declaringClassName;
    private final Field field;
    private final boolean createIfAbsent;

    private JustMockDefinition(Class<?> type, String fieldName, String explicitBeanName,
                                Set<Annotation> qualifierAnnotations,
                                String declaringClassName, Field field, boolean createIfAbsent) {
        this.type = type;
        this.fieldName = fieldName;
        this.explicitBeanName = explicitBeanName;
        this.qualifierAnnotations = Collections.unmodifiableSet(
                new LinkedHashSet<>(qualifierAnnotations));
        this.declaringClassName = declaringClassName;
        this.field = field;
        this.createIfAbsent = createIfAbsent;
    }

    static JustMockDefinition forField(Field field, JustMock justMock) {
        Set<Annotation> qualifiers = new LinkedHashSet<>();
        for (Annotation annotation : field.getAnnotations()) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (annotationType == Qualifier.class
                    || AnnotatedElementUtils.hasAnnotation(annotationType, Qualifier.class)) {
                qualifiers.add(annotation);
            }
        }
        return new JustMockDefinition(field.getType(), field.getName(), justMock.name(),
                qualifiers, field.getDeclaringClass().getName(), field, false);
    }

    static JustMockDefinition forBeanName(Class<?> type, String beanName, String source) {
        return new JustMockDefinition(type, beanName, beanName,
                Collections.emptySet(), source, null, false);
    }

    static JustMockDefinition forAutoMock(Class<?> type, String existingBeanName, String source) {
        boolean createIfAbsent = existingBeanName == null || existingBeanName.isEmpty();
        String beanName = createIfAbsent ? decapitalize(type.getSimpleName()) : existingBeanName;
        return new JustMockDefinition(type, beanName, beanName,
                Collections.emptySet(), source, null, createIfAbsent);
    }

    static String decapitalize(String simpleName) {
        if (simpleName == null || simpleName.isEmpty()) {
            return simpleName;
        }
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }

    Class<?> getType() { return type; }
    String getFieldName() { return fieldName; }
    String getExplicitBeanName() { return explicitBeanName; }
    String getDeclaringClassName() { return declaringClassName; }
    boolean hasQualifierAnnotations() { return !qualifierAnnotations.isEmpty(); }
    boolean isCreateIfAbsent() { return createIfAbsent; }

    DependencyDescriptor toDependencyDescriptor() {
        if (field == null) {
            throw new IllegalStateException("No dependency field available for " + describe());
        }
        return new DependencyDescriptor(field, true);
    }

    String describe() {
        return declaringClassName + "." + fieldName + " (" + type.getName() + ")";
    }

    /**
     * 诊断用的 mock 来源标签：{@code @JustMock}、{@code @ThreadScopedMock}，
     * 或 auto-mock 来源字符串。
     */
    String sourceLabel() {
        if (field != null) {
            return "@JustMock";
        }
        if (declaringClassName != null && !declaringClassName.isEmpty()) {
            return declaringClassName;
        }
        return "@JustMock";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof JustMockDefinition)) return false;
        JustMockDefinition that = (JustMockDefinition) other;
        if (!Objects.equals(type, that.type)
                || !Objects.equals(explicitBeanName, that.explicitBeanName)) {
            return false;
        }
        if (!explicitBeanName.isEmpty()) {
            return true;
        }
        return Objects.equals(fieldName, that.fieldName)
                && Objects.equals(qualifierAnnotations, that.qualifierAnnotations);
    }

    @Override
    public int hashCode() {
        if (!explicitBeanName.isEmpty()) {
            return Objects.hash(type, explicitBeanName);
        }
        return Objects.hash(type, fieldName, explicitBeanName, qualifierAnnotations);
    }
}

package com.just.test.smarttest.mock;

import com.just.test.smarttest.annotation.SmartMock;
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
 * 一个 {@code @SmartMock} 字段表达的 Bean 选择意图。
 *
 * <p>声明类只用于诊断；Context 缓存只关心会影响 Bean 选择的语义字段，
 * 使不同测试类的等价声明能够复用 Context。</p>
 */
final class SmartMockDefinition {

    private final Class<?> type;
    private final String fieldName;
    private final String explicitBeanName;
    private final Set<Annotation> qualifierAnnotations;
    private final String declaringClassName;
    private final Field field;

    private SmartMockDefinition(Class<?> type, String fieldName, String explicitBeanName,
                                Set<Annotation> qualifierAnnotations,
                                String declaringClassName, Field field) {
        this.type = type;
        this.fieldName = fieldName;
        this.explicitBeanName = explicitBeanName;
        this.qualifierAnnotations = Collections.unmodifiableSet(
                new LinkedHashSet<>(qualifierAnnotations));
        this.declaringClassName = declaringClassName;
        this.field = field;
    }

    static SmartMockDefinition forField(Field field, SmartMock smartMock) {
        Set<Annotation> qualifiers = new LinkedHashSet<>();
        for (Annotation annotation : field.getAnnotations()) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (annotationType == Qualifier.class
                    || AnnotatedElementUtils.hasAnnotation(annotationType, Qualifier.class)) {
                qualifiers.add(annotation);
            }
        }
        return new SmartMockDefinition(field.getType(), field.getName(), smartMock.name(),
                qualifiers, field.getDeclaringClass().getName(), field);
    }

    static SmartMockDefinition forBeanName(Class<?> type, String beanName, String source) {
        return new SmartMockDefinition(type, beanName, beanName,
                Collections.emptySet(), source, null);
    }

    Class<?> getType() { return type; }
    String getFieldName() { return fieldName; }
    String getExplicitBeanName() { return explicitBeanName; }
    String getDeclaringClassName() { return declaringClassName; }
    boolean hasQualifierAnnotations() { return !qualifierAnnotations.isEmpty(); }

    DependencyDescriptor toDependencyDescriptor() {
        if (field == null) {
            throw new IllegalStateException("No dependency field available for " + describe());
        }
        return new DependencyDescriptor(field, true);
    }

    String describe() {
        return declaringClassName + "." + fieldName + " (" + type.getName() + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SmartMockDefinition)) return false;
        SmartMockDefinition that = (SmartMockDefinition) other;
        return Objects.equals(type, that.type)
                && Objects.equals(fieldName, that.fieldName)
                && Objects.equals(explicitBeanName, that.explicitBeanName)
                && Objects.equals(qualifierAnnotations, that.qualifierAnnotations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, fieldName, explicitBeanName, qualifierAnnotations);
    }
}

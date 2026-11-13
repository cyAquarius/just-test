package com.just.test.internal.mock;

import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustSpy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.beans.Introspector;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 一个 {@code @JustMock} 或 {@code @JustSpy} 字段表达的 Bean 选择意图。
 *
 * <p>声明类只用于诊断；Context 缓存只关心会影响 Bean 选择的语义字段，
 * 使不同测试类的等价声明能够复用 Context。{@code spy} 会进入 equals / hashCode，
 * 因此同一选择下的 mock 与 spy 不会共用 Spring Test Context。</p>
 */
final class JustMockDefinition {

    private final Class<?> type;
    private final String fieldName;
    private final String explicitBeanName;
    private final Set<Annotation> qualifierAnnotations;
    private final String declaringClassName;
    private final Field field;
    private final boolean createIfAbsent;
    private final boolean spy;

    private JustMockDefinition(Class<?> type, String fieldName, String explicitBeanName,
                                Set<Annotation> qualifierAnnotations,
                                String declaringClassName, Field field,
                                boolean createIfAbsent, boolean spy) {
        this.type = type;
        this.fieldName = fieldName;
        this.explicitBeanName = explicitBeanName;
        this.qualifierAnnotations = Collections.unmodifiableSet(
                new LinkedHashSet<>(qualifierAnnotations));
        this.declaringClassName = declaringClassName;
        this.field = field;
        this.createIfAbsent = createIfAbsent;
        this.spy = spy;
    }

    static JustMockDefinition forField(Field field, JustMock justMock) {
        rejectUnsupportedFieldType(field);
        return new JustMockDefinition(field.getType(), field.getName(), justMock.name(),
                qualifiers(field), field.getDeclaringClass().getName(), field, false, false);
    }

    static JustMockDefinition forSpyField(Field field, JustSpy justSpy) {
        rejectUnsupportedFieldType(field);
        return new JustMockDefinition(field.getType(), field.getName(), justSpy.name(),
                qualifiers(field), field.getDeclaringClass().getName(), field, false, true);
    }

    /**
     * primitive 和数组不能作为 Mockito mock 的类型。在字段入口失败，避免拖到注入期才报错。
     */
    private static void rejectUnsupportedFieldType(Field field) {
        Class<?> type = field.getType();
        if (!type.isPrimitive() && !type.isArray()) {
            return;
        }
        String kind = type.isPrimitive() ? "a primitive" : "an array";
        throw new IllegalStateException("[JustMock] " + field.getDeclaringClass().getName()
                + "." + field.getName() + " is " + kind + " (" + type.getName()
                + "). @JustMock and @JustSpy require a class or interface.");
    }

    private static Set<Annotation> qualifiers(Field field) {
        Set<Annotation> qualifiers = new LinkedHashSet<>();
        for (Annotation annotation : field.getAnnotations()) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (annotationType == Qualifier.class
                    || AnnotatedElementUtils.hasAnnotation(annotationType, Qualifier.class)) {
                qualifiers.add(annotation);
            }
        }
        return qualifiers;
    }

    static JustMockDefinition forBeanName(Class<?> type, String beanName, String source) {
        return new JustMockDefinition(type, beanName, beanName,
                Collections.emptySet(), source, null, false, false);
    }

    static JustMockDefinition forAutoMock(Class<?> type, String existingBeanName, String source) {
        boolean createIfAbsent = existingBeanName == null || existingBeanName.isEmpty();
        String beanName = createIfAbsent ? decapitalize(type.getSimpleName()) : existingBeanName;
        return new JustMockDefinition(type, beanName, beanName,
                Collections.emptySet(), source, null, createIfAbsent, false);
    }

    /**
     * JavaBeans 规则：前两个字符都是大写时保持原样，因此 {@code URLClient} 仍是 {@code URLClient}，
     * 而不是 {@code uRLClient}。与 Spring {@code AnnotationBeanNameGenerator} 使用的
     * {@link Introspector#decapitalize(String)} 一致。
     */
    static String decapitalize(String simpleName) {
        if (simpleName == null || simpleName.isEmpty()) {
            return simpleName;
        }
        return Introspector.decapitalize(simpleName);
    }

    Class<?> getType() { return type; }
    String getFieldName() { return fieldName; }
    String getExplicitBeanName() { return explicitBeanName; }
    String getDeclaringClassName() { return declaringClassName; }
    boolean hasQualifierAnnotations() { return !qualifierAnnotations.isEmpty(); }
    boolean isCreateIfAbsent() { return createIfAbsent; }
    boolean isSpy() { return spy; }

    /** 测试字段上的 {@code @JustMock} / {@code @JustSpy}。全局来源（{@code @ThreadScopedMock}、Feign auto-mock）没有字段。 */
    boolean isFieldDeclaration() { return field != null; }

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
     * 诊断用的来源标签：{@code @JustMock}、{@code @JustSpy}、{@code @ThreadScopedMock}，
     * 或 auto-mock 来源字符串。
     */
    String sourceLabel() {
        if (field != null) {
            return spy ? "@JustSpy" : "@JustMock";
        }
        if (declaringClassName != null && !declaringClassName.isEmpty()) {
            return declaringClassName;
        }
        return spy ? "@JustSpy" : "@JustMock";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof JustMockDefinition)) return false;
        JustMockDefinition that = (JustMockDefinition) other;
        if (!Objects.equals(type, that.type)
                || !Objects.equals(explicitBeanName, that.explicitBeanName)
                || spy != that.spy) {
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
            return Objects.hash(type, explicitBeanName, spy);
        }
        return Objects.hash(type, fieldName, explicitBeanName, qualifierAnnotations, spy);
    }
}

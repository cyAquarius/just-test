package com.just.test.smarttest.mock;

import java.util.Objects;

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
    private final String qualifier;
    private final String declaringClassName;

    SmartMockDefinition(Class<?> type, String fieldName, String explicitBeanName,
                        String qualifier, String declaringClassName) {
        this.type = type;
        this.fieldName = fieldName;
        this.explicitBeanName = explicitBeanName;
        this.qualifier = qualifier;
        this.declaringClassName = declaringClassName;
    }

    Class<?> getType() { return type; }
    String getFieldName() { return fieldName; }
    String getExplicitBeanName() { return explicitBeanName; }
    String getQualifier() { return qualifier; }
    String getDeclaringClassName() { return declaringClassName; }

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
                && Objects.equals(qualifier, that.qualifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, fieldName, explicitBeanName, qualifier);
    }
}

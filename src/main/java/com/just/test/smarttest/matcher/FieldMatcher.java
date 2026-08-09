package com.just.test.smarttest.matcher;

/**
 * 字段匹配器接口，用于 expect 验证时的灵活匹配。
 */
public interface FieldMatcher {

    /**
     * 判断实际值是否匹配期望。
     *
     * @param actualValue 数据库中的实际值
     * @return true 表示匹配通过
     */
    boolean matches(Object actualValue);

    /**
     * 匹配失败时的描述信息。
     */
    String describe();
}

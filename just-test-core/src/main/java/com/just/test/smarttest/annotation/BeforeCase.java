package com.just.test.smarttest.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 per-case 的 mock 设置方法。当 case 名称匹配时，
 * SmartTest 在 JUnit {@code @BeforeEach} 之后、测试方法执行前自动调用该方法。
 *
 * <pre>
 * {@code @BeforeCase("deductBalance")}
 * void setupDeductBalance() {
 *     when(feignClient.query(anyLong())).thenReturn(mockResult);
 * }
 * </pre>
 *
 * <p>一个测试类中可以有多个 {@code @BeforeCase} 方法，分别对应不同的 case。
 * 没有匹配的 case 不会触发任何 {@code @BeforeCase} 方法。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BeforeCase {

    /**
     * 要匹配的 case 名称（即 case 子目录名）。
     */
    String value();
}

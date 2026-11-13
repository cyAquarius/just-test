package com.just.test.mock.intercept.disabledscan;

import org.junit.jupiter.api.Disabled;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自身带 {@code @Disabled} 的组合注解。标了它的测试类不会被 JUnit 执行。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Disabled
public @interface DisabledScanOff {
}

package com.just.test.smarttest.annotation;

import com.just.test.smarttest.context.CaseArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据驱动参数化注解。标记在 {@code @ParameterizedTest} 方法上，
 * 默认优先扫描“测试类同包/测试类简单名”下的 case 子目录，
 * 并兼容原有的测试类同包目录；每个子目录生成一个 {@code CaseContext} 参数。
 *
 * <pre>
 * {@code @ParameterizedTest}
 * {@code @CaseSource}
 * void test(CaseContext ctx) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ArgumentsSource(CaseArgumentsProvider.class)
public @interface CaseSource {
    /**
     * case 根目录，默认优先使用“测试类简单名”子目录；找不到时兼容扫描测试类同包目录。
     */
    String value() default "";
}

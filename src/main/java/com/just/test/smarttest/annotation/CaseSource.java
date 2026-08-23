package com.just.test.smarttest.annotation;

import com.just.test.smarttest.context.CaseTemplateInvocationContextProvider;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SmartTest 数据驱动测试入口。默认优先扫描“测试类同包/测试类简单名”下的 case 子目录，
 * 并兼容原有的测试类同包目录；每个子目录生成一个具有完整 JUnit 生命周期的 case invocation。
 *
 * <p>仅用于实现 {@code SmartTestLifecycle} 的 {@link SmartTest} 测试类；该注解本身就是
 * JUnit 测试注解，不要再与 {@code @Test}、{@code @RepeatedTest} 或其他测试注解组合。</p>
 *
 * <pre>
 * {@code @CaseSource}
 * void test(CaseContext ctx) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(CaseTemplateInvocationContextProvider.class)
public @interface CaseSource {
    /**
     * case 根目录，默认优先使用“测试类简单名”子目录；找不到时兼容扫描测试类同包目录。
     */
    String value() default "";
}

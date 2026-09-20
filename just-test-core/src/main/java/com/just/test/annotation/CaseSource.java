package com.just.test.annotation;

import com.just.test.internal.context.CaseTemplateInvocationContextProvider;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * JustTest 数据驱动测试入口。默认扫描测试类所在包（{@code packagePath}）下的
 * case 子目录；每个子目录生成一个具有完整 JUnit 生命周期的 case invocation。
 *
 * <p>仅用于实现 {@code JustTestLifecycle} 的 {@link JustTest} 测试类；该注解本身就是
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
     * case 根目录。空值表示测试类所在包 {@code packagePath}；
     * 显式值解析为 {@code {packagePath}/{value}/}。
     * 不会探测 {@code {packagePath}/{SimpleClassName}/}，也不会静默回退到其他根。
     * 同一包内不得有多个具体（非 abstract）{@code @JustTest} 类。
     * 抽象 Support / 基类不得标注 {@code @JustTest}；具体测试类必须自身直接标注。
     */
    String value() default "";
}

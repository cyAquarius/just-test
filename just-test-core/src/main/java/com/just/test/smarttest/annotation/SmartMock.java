package com.just.test.smarttest.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 线程安全的 Mock 注解，替代 {@code @MockBean} 解决并行测试下 mock 互相覆盖的问题。
 *
 * <p>底层通过线程作用域 ScopedProxy 实现：每个线程拿到独立的 Mockito mock 实例，
 * when() stubbing 天然线程隔离，与 H2 数据库路由完全对称。</p>
 *
 * <p>字段只在 {@link SmartTest} 的 {@link CaseSource} invocation 生命周期内注入；
 * 它不是普通 JUnit 测试类中的通用 Mock 注解。</p>
 *
 * <p>使用方式与 {@code @MockBean} 一致：</p>
 * <pre>
 * class SomeTest extends BaseTest {
 *     &#64;SmartMock
 *     protected SomeService someService;
 *
 *     &#64;BeforeCase("caseName")
 *     void setup() {
 *         when(someService.doSomething()).thenReturn("result");
 *     }
 * }
 * </pre>
 *
 * <p>可以放在抽象基类或具体测试类上，灵活组合不会冲突。</p>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SmartMock {
    /** 显式指定要替换的 Bean 名；未指定时按 Spring 的常见注入语义解析。 */
    String name() default "";
}

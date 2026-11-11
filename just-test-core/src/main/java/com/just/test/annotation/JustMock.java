package com.just.test.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 线程安全的 Mock 注解，替代 {@code @MockBean} 解决并行测试下 mock 互相覆盖的问题。
 *
 * <p>底层通过线程作用域 ScopedProxy 实现：每个线程拿到独立的 Mockito mock 实例，
 * when() stubbing 天然线程隔离，与 H2 数据库路由完全对称。这个线程作用域实例不会再被
 * Spring 自动代理包裹，因此被替换的具体类即使带 {@code @Repository} 等会被自动代理的注解，
 * 测试字段仍然是可 stub 的 Mockito mock。</p>
 *
 * <p>字段只在 {@code @JustTest} 的 {@link CaseSource} invocation 生命周期内注入；
 * 它不是普通 JUnit 测试类中的通用 Mock 注解。</p>
 *
 * <p>使用方式与 {@code @MockBean} 一致：</p>
 * <pre>
 * &#64;JustTest
 * class SomeTest implements JustTestLifecycle {
 *     &#64;JustMock
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
 *
 * <p>未 stub 的方法只会返回 Mockito 默认值。要保留已有 Bean、只替换个别方法时使用
 * {@link JustSpy}，不要在本注解上增加 spy 开关。</p>
 *
 * <p>若目标 Bean 已经是线程级 mock（{@link ThreadScopedMock} 的 {@code @Bean}，或
 * {@code @JustTestProject(autoMockFeignClients = true)} 创建的 Feign mock），本注解只绑定字段：
 * 不重复注册 Bean 定义，也不把这条声明放进 Spring Test 上下文缓存 key。注入时会解开 scoped proxy，
 * {@code when(field...)} 与 {@code doReturn(...).when(field)} 直接打在字段上。真实 Bean 仍会替换定义，
 * 并继续参与缓存 key。</p>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JustMock {
    /** 显式指定要替换或绑定的 Bean 名；未指定时按 Spring 的常见注入语义解析。 */
    String name() default "";
}

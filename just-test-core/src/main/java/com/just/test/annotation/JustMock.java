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
 * {@code when(field...)} 与 {@code doReturn(...).when(field)} 直接打在字段上。</p>
 *
 * <p>若目标是真实 Bean，并且测试使用 {@code @JustTestProject}，本注解同样只绑定：
 * 启动时该类型已经包成线程级 Mockito mock，真实 Bean 被藏起来，advisor 留在真实 Bean 上。
 * 当前测试类声明了本注解时，未 stub 的调用返回 Mockito 默认值；没有声明的测试类仍调用真实 Bean。
 * 激活看的是「本测试类声明了 {@code @JustMock}」，不是「当前有 stub」。case 结束会重置 stub。
 * 这条声明不进入上下文缓存 key。类型全集在该共享 Context 第一次启动前确定，之后不能再增加
 * （{@code @JustTestProject(interceptMocks)}，加上该启动类所在包及其子包里、解析出的启动类
 * 就是这个 {@code @JustTestProject} 类的 {@code @JustTest} 扫描结果）。
 * 包树之外的测试若需要拦截，必须把类型写进 {@code interceptMocks}。
 * 接口与实现解析到同一个 Bean 时只注册一次拦截，两个字段绑定该线程级 mock；
 * 当前测试类声明了其中任一类型即激活。无法安全合并（mock 类型不兼容、多个候选 Bean）时在启动期失败，
 * 并列出声明类型、Bean 名和声明它们的测试类。
 * 没装上拦截的类型会 fail-fast，不会退回替换定义。真实 Bean 内部的 {@code this.foo()} 自调不会进拦截。
 * 没有 {@code @JustTestProject} 时，真实 Bean 仍会替换定义并参与缓存 key。</p>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JustMock {
    /** 显式指定要替换或绑定的 Bean 名；未指定时按 Spring 的常见注入语义解析。 */
    String name() default "";
}

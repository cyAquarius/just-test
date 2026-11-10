package com.just.test.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 对已有 Spring Bean 做线程隔离的部分 mock：只 stub 个别方法，其余调用委托给真实 Bean。
 *
 * <p>与 {@link JustMock} 共用线程作用域 ScopedProxy、case 开始时的
 * {@code resetCurrentThread} 与预热，以及测试字段注入。每个 case 线程拿到一个新的
 * Mockito mock，stub 不会带到下一个 case。未 stub 的调用委托给隐藏的真实 Bean
 * （通常是 singleton），因此 {@code prepare.yaml} / H2 数据仍然生效。</p>
 *
 * <p>委托写法是 {@code mock(type, withSettings().defaultAnswer(delegatesTo(realBean)))}，
 * 不是 {@code Mockito.spy}。MyBatis Mapper、Feign 这类 JDK / 接口代理因此也能包裹。
 * 委托目标是容器里暴露的那个 Bean：若它本身是 Spring AOP 代理（例如 {@code @Transactional}），
 * 未 stub 的调用走该代理及其 advisor，不会拆到被代理的 target。</p>
 *
 * <p>真实 Bean 只在 Context refresh 时按原定义创建。BeanFactoryPostProcessor 只挪动定义，
 * 把它藏成非 autowire candidate，避免同类型出现第二个可注入 Bean。</p>
 *
 * <p>Stub 必须用 {@code doReturn} / {@code doThrow} / {@code doNothing}。
 * {@code when(spy.foo())} 会先执行真实方法。真实 Bean 内部的 {@code this.bar()}
 * 不会经过 spy，因此不会命中 stub。</p>
 *
 * <p>必须能按与 {@link JustMock} 相同的 name / {@code @Qualifier} / 候选规则找到已有 Bean；
 * 找不到则启动失败，不会 create-if-absent。同一字段不能同时标注 {@link JustMock}。
 * 同一类型，或解析到同一个 Bean 时，不能再被 {@link JustMock} / {@link ThreadScopedMock}
 * 替换，启动期给出类级诊断。显式 {@code @JustSpy} 优先于
 * {@code @JustTestProject(autoMockFeignClients)}，该类型不会再被自动 mock。</p>
 *
 * <pre>
 * &#64;JustSpy
 * private FormDao formDao;
 *
 * &#64;Override
 * public void beforeExecute(CaseContext ctx) {
 *     doReturn("").when(formDao).getTaskChildIds(anyLong());
 * }
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JustSpy {
    /** 显式指定要包裹的 Bean 名；未指定时按 Spring 的常见注入语义解析。 */
    String name() default "";
}

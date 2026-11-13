package com.just.test.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 @Bean 方法为线程隔离 mock：每个线程持有独立实例，并行 stubbing 无竞态。
 *
 * <p>框架将带此注解的 {@code @Bean} 定义替换为线程作用域 ScopedProxy，
 * 与 {@code @JustMock} 字段注解同一套机制。</p>
 *
 * <p>方法只声明类型和 Bean 名。框架忽略方法体，始终自己创建线程作用域 mock。
 * 方法体里的自定义 {@code MockSettings}（{@code defaultAnswer}、name、
 * {@code extraInterfaces}）或手写 fake 不会生效。定制 stub 写在
 * {@code beforeExecute} 或 {@link BeforeCase}。</p>
 *
 * <p>实例仅能在活动 JustTest case 线程中访问；异步线程不会继承 case 上下文。</p>
 *
 * <p>适用场景：{@code @Configuration} 里的全局外部依赖 mock（Feign Client、Redis、Cos 等）。
 * 并行类间执行时，共享 Spring Context 导致同一 mock 实例被多线程同时 stub / invoke，
 * Mockito 内部 {@code InvocationContainerImpl.stubbed} 是 LinkedList 非线程安全，
 * 表现为偶发 stub 失效（返回默认 null）或 NPE。</p>
 *
 * <pre>
 * &#64;Configuration
 * public class DemoMockConfiguration {
 *     &#64;Bean
 *     &#64;ThreadScopedMock
 *     public TaskCenterClient taskCenterClient() {
 *         return null; // 方法体不会执行。stub 写在 beforeExecute 或 @BeforeCase
 *     }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ThreadScopedMock {
}

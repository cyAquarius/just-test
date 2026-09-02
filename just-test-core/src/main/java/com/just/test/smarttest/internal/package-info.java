/**
 * SmartTest 引擎内部实现。
 *
 * <p>本包及其子包不是稳定消费 API，不提供兼容承诺，可随时变更。
 * 应用测试应只依赖 {@code com.just.test.smarttest.annotation}、
 * {@link com.just.test.smarttest.context.CaseContext}、
 * {@link com.just.test.smarttest.lifecycle.SmartTestLifecycle}、
 * {@link com.just.test.smarttest.mock.StaticMockContext}
 * 以及 Boot 模块的 {@code @SmartTest}（及其元注解校验扩展）。</p>
 *
 * <p>为满足 JUnit {@code @ExtendWith}、Spring {@code spring.factories}
 * 与 {@code @Configuration} 注册，部分类型必须保持 public；
 * 这不表示它们属于公开 API。</p>
 */
package com.just.test.smarttest.internal;

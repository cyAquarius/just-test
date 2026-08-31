package com.just.test.smarttest.lifecycle;

import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.mock.StaticMockContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 生命周期钩子接口（高级扩展点）。
 *
 * <p>所有 {@code @SmartTest} 测试基类必须实现此接口。
 * 6 个方法均提供空 default 实现，框架默认行为已覆盖绝大多数场景。
 * 仅在需要接管框架默认验证逻辑时才覆写对应方法。</p>
 *
 * <p>典型用法：覆写 {@code verifyResult} 实现自定义返回值断言，
 * 返回 {@code true} 表示已处理，框架将跳过 response.yaml 自动验证。</p>
 */
public interface SmartTestLifecycle {

    /** 配置当前 case 线程的静态 Mock；在 JUnit @BeforeEach 前创建，在 @AfterEach 后关闭。 */
    default void configureStaticMocks(CaseContext ctx, StaticMockContext mocks) {}

    /** 测试方法执行前（JUnit @BeforeEach 与 @BeforeCase 之后） */
    default void beforeExecute(CaseContext ctx) {}

    /** 测试方法执行后（验证与 JUnit @AfterEach 之前） */
    default void afterExecute(CaseContext ctx) {}

    /** 返回值验证。return true = 已处理，跳过 response.yaml */
    default boolean verifyResult(CaseContext ctx) { return false; }

    /** 异常验证。return true = 已处理，跳过 expect_exception.yaml */
    default boolean verifyException(CaseContext ctx) { return false; }

    /** DB 验证。return true = 已处理，跳过 expect.yaml */
    default boolean verifyDatabase(CaseContext ctx, JdbcTemplate jdbcTemplate) { return false; }
}

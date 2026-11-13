package com.just.test.internal.loader.ymlstage;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

/**
 * Surefire 不会执行本类。由 {@link YmlStageFileContractTest} 通过 EngineTestKit 启动。
 *
 * <p>{@code yml-applied} / {@code yaml-applied} 必须读到 request 与 prepare。
 * {@code yml-expect-miss} 与 {@code yml-response-miss} 的 Java 方法本身会通过，
 * 只有对应的 {@code .yml} 断言被加载时才会失败。
 * {@code dual-prepare} 在只加载 {@code .yaml} 时也会通过。</p>
 */
@JustTest
@ContextConfiguration(classes = YmlStageFileCases.EmptyConfiguration.class)
class YmlStageFileCases implements JustTestLifecycle {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @CaseSource
    void run(CaseContext context) {
        String caseName = context.getCaseName();
        if ("yml-applied".equals(caseName) || "yaml-applied".equals(caseName)) {
            String marker = context.getString("marker");
            if (marker == null) {
                throw new AssertionError("request was not loaded for " + caseName);
            }
            Number count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM parallel_case_record WHERE id = 1 AND marker = ?",
                    Number.class,
                    marker);
            if (count == null || count.intValue() != 1) {
                throw new AssertionError("prepare was not applied for " + caseName);
            }
            context.setResult(marker);
            return;
        }
        if ("yml-expect-miss".equals(caseName) || "yml-response-miss".equals(caseName)) {
            context.setResult("prepared");
            return;
        }
        if ("dual-prepare".equals(caseName)) {
            context.setResult("ok");
            return;
        }
        throw new AssertionError("unexpected case " + caseName);
    }

    @Configuration
    static class EmptyConfiguration {
    }
}

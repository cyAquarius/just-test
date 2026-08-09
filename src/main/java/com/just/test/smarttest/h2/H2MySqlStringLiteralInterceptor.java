package com.just.test.smarttest.h2;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.sql.Connection;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * MyBatis 拦截器：将 MySQL SQL 中的双引号字符串字面量 {@code "..."} 改写为单引号 {@code '...'}。
 * <p>
 * <b>背景：</b>MySQL 默认 {@code sql_mode} 不启用 {@code ANSI_QUOTES}，双引号等价于单引号用作字符串字面量。
 * 项目生产 SQL 里偶尔写成 {@code REPLACE(col,",","")} 这种形式。
 * 而 H2（包括 {@code MODE=MySQL}）始终把 {@code "..."} 当作标识符引用，
 * 导致 {@code ","} 被解析为列名并报 {@code Column "," not found}。
 * <p>
 * <b>策略：</b>仅当 SQL 中出现触发关键字（{@code REPLACE(} / {@code CONCAT_WS(} 等 MySQL 字符串函数）
 * 时才改写，避免误伤使用双引号限定的标识符（虽然本项目统一用反引号，但保守起见）。
 * 改写规则：将所有 {@code "X"}（{@code X} 不含双引号）替换为 {@code 'X'}，
 * 并把原字符串内的单引号转义 {@code "'"} → {@code ''''}。
 * <p>
 * <b>范围：</b>仅在测试环境（src/test）生效，不影响生产代码和生产 SQL。
 *
 * @see H2MySqlIfInterceptor 同类拦截器：IF(...) → CASEWHEN(...)
 */
@Intercepts({
        @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
public class H2MySqlStringLiteralInterceptor implements Interceptor {

    /** 触发改写的 MySQL 字符串函数（不区分大小写）。命中其一才执行改写,避免误伤。 */
    private static final Pattern TRIGGER_PATTERN = Pattern.compile(
            "\\b(?:REPLACE|CONCAT_WS|INSERT|LOCATE|INSTR|POSITION|SUBSTRING_INDEX)\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler handler = (StatementHandler) invocation.getTarget();
        MetaObject metaObject = SystemMetaObject.forObject(handler);
        // MyBatis 多拦截器链会把 target 层层包成 Plugin Proxy,
        // 这里逐层解包直到拿到真实的 RoutingStatementHandler。
        while (metaObject.hasGetter("h.target")) {
            metaObject = SystemMetaObject.forObject(metaObject.getValue("h.target"));
        }
        String originalSql = (String) metaObject.getValue("delegate.boundSql.sql");

        if (originalSql != null && TRIGGER_PATTERN.matcher(originalSql).find()) {
            String rewritten = rewriteDoubleQuotedLiterals(originalSql);
            if (!rewritten.equals(originalSql)) {
                metaObject.setValue("delegate.boundSql.sql", rewritten);
            }
        }

        return invocation.proceed();
    }

    /** 将 {@code "X"} 改写为 {@code 'X'}，内部单引号转义。 */
    static String rewriteDoubleQuotedLiterals(String sql) {
        return SqlTextRewriter.rewriteDoubleQuotedLiterals(sql);
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 无需配置
    }
}

package com.just.test.smarttest.h2;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.sql.Connection;
import java.util.Properties;

/**
 * MyBatis 拦截器：将 MySQL {@code IF(condition, trueVal, falseVal)} 改写为
 * H2 内置的 {@code CASEWHEN(condition, trueVal, falseVal)}。
 * <p>
 * H2 1.4.200 中 {@code IF} 是 SQL 保留字（过程式 IF...THEN...ELSE），
 * 即使通过 {@code CREATE ALIAS "IF"} 注册函数别名，裸写 {@code if(...)}
 * 在 SELECT/JOIN 等上下文中仍会被解析器当作关键字而报语法错误。
 * <p>
 * {@code CASEWHEN} 是 H2 内置函数，语义与 MySQL IF() 完全一致，
 * 无需 CREATE ALIAS，所有 SQL 上下文均可用。
 * <p>
 * 仅在测试环境生效（src/test），不影响生产代码。
 */
@Intercepts({
        @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
public class H2MySqlIfInterceptor implements Interceptor {

    /**
     * 匹配 SQL 中的 IF( — 仅匹配函数调用形式，不匹配 IF EXISTS / IF NOT EXISTS。
     * <p>
     * 正则说明：
     * <ul>
     *   <li>{@code (?i)} — 大小写不敏感</li>
     *   <li>{@code \bIF\s*\(} — 单词边界 + IF + 可选空白 + 左括号</li>
     * </ul>
     * 负向前瞻排除 {@code IF NOT EXISTS} / {@code IF EXISTS}（DDL 语法）不在此列，
     * 因为 MyBatis mapper 中不会出现这类 DDL。
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler handler = (StatementHandler) invocation.getTarget();
        MetaObject metaObject = SystemMetaObject.forObject(handler);
        while (metaObject.hasGetter("h.target")) {
            metaObject = SystemMetaObject.forObject(metaObject.getValue("h.target"));
        }
        String originalSql = (String) metaObject.getValue("delegate.boundSql.sql");

        String rewrittenSql = SqlTextRewriter.rewriteIfFunctions(originalSql);
        if (originalSql != null && !originalSql.equals(rewrittenSql)) {
            metaObject.setValue("delegate.boundSql.sql", rewrittenSql);
        }

        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 无需额外配置
    }
}

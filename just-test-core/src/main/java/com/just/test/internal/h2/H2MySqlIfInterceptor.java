package com.just.test.internal.h2;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.sql.Connection;
import java.util.Properties;

/**
 * MyBatis 拦截器：经 {@link SqlTextRewriter} 将 Mapper SQL 中的
 * {@code IF(...)} 改为 H2 {@code CASEWHEN(...)}，
 * {@code DATE_FORMAT(...)} 改为 {@code FORMATDATETIME(...)}。
 *
 * <p>只处理函数调用形式（标识符后跟 {@code (}），因此不会改写 {@code IF EXISTS}。
 * 由 JustTest 装到消费测试的 {@code SqlSessionFactory} 上；纯 {@code JdbcTemplate}
 * SQL 不走此拦截器。生产 Mapper 不受影响。</p>
 */
@Intercepts({
        @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
public class H2MySqlIfInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler handler = (StatementHandler) invocation.getTarget();
        MetaObject metaObject = SystemMetaObject.forObject(MyBatisPluginTargetResolver.unwrap(handler));
        String originalSql = (String) metaObject.getValue("delegate.boundSql.sql");

        String rewrittenSql = SqlTextRewriter.rewriteDateFormatFunctions(
                SqlTextRewriter.rewriteIfFunctions(originalSql));
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

package com.just.test.smarttest.h2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 将 {@link MySqlCompatFunctions} 中的 Java 方法注册为 H2 自定义函数。
 * <p>
 * 在 SchemaInitializer 完成建表后调用 {@link #register(JdbcTemplate)}，
 * 使 mapper XML 中的 MySQL 特有函数在 H2 上可用。
 * <p>
 * 注册采用 {@code CREATE ALIAS IF NOT EXISTS}，重复调用安全。
 */
public final class H2FunctionRegistrar {

    private static final Logger log = LoggerFactory.getLogger(H2FunctionRegistrar.class);

    private static final String FUNC_CLASS = MySqlCompatFunctions.class.getName();

    /** 需要注册的函数映射：SQL函数名 → Java方法名 */
    private static final String[][] FUNCTION_MAPPINGS = {
            {"DATE_FORMAT", "dateFormat"},
            {"FIND_IN_SET", "findInSet"},
    };

    private H2FunctionRegistrar() {
    }

    /**
     * 向指定 H2 数据库注册所有 MySQL 兼容函数。
     * <p>
     * 注意：MySQL IF() 不在此注册 — IF 是 H2 SQL 保留字，CREATE ALIAS 无法覆盖
     * 所有 SQL 上下文。改由 {@link H2MySqlIfInterceptor} 在 MyBatis 层将
     * IF(...) 改写为 H2 内置的 CASEWHEN(...)。
     *
     * @param jdbcTemplate 目标 H2 数据库的 JdbcTemplate
     */
    public static void register(JdbcTemplate jdbcTemplate) {
        int count = 0;

        for (String[] mapping : FUNCTION_MAPPINGS) {
            String sql = "CREATE ALIAS IF NOT EXISTS " + mapping[0]
                    + " FOR \"" + FUNC_CLASS + "." + mapping[1] + "\"";
            try {
                jdbcTemplate.execute(sql);
                count++;
            } catch (Exception e) {
                log.warn("[SmartTest] Failed to register H2 function {}: {}", mapping[0], e.getMessage());
            }
        }

        log.info("[SmartTest] Registered {} MySQL-compat functions in H2", count);
    }
}

package com.just.test.smarttest.internal.h2;

import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.internal.context.CaseExecutionContext;
import com.just.test.smarttest.internal.datasource.SchemaInitializer;
import com.just.test.smarttest.internal.datasource.SmartTestRoutingDataSource;
import com.just.test.smarttest.internal.loader.DataSetLoader;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class H2CompatibilityTest {

    private static final String URL = "jdbc:h2:mem:{key};MODE=MySQL;"
            + "DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";

    @Test
    void executesCleanedMySqlDdlOnH2() {
        SmartTestRoutingDataSource dataSource = new SmartTestRoutingDataSource(URL);
        try {
            CaseExecutionContext.bind(new CaseContext("mysql-ddl", "test/mysql-ddl"));
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            SchemaInitializer.initialize(jdbcTemplate, "classpath:sql/schema-mysql-compat.sql");

            jdbcTemplate.update("INSERT INTO mysql_compat(name) VALUES ('sample')");

            assertEquals(1L, jdbcTemplate.queryForObject(
                    "SELECT id FROM mysql_compat", Long.class));

            jdbcTemplate.update("DELETE FROM mysql_compat");
            DataSetLoader.cleanTables(jdbcTemplate);
            jdbcTemplate.update("INSERT INTO mysql_compat(name) VALUES ('after-delete')");

            assertEquals(1L, jdbcTemplate.queryForObject(
                    "SELECT id FROM mysql_compat", Long.class));
            assertEquals(false, jdbcTemplate.queryForObject(
                    "SELECT enabled FROM mysql_compat", Boolean.class));

            DataSetLoader.cleanTables(jdbcTemplate);
            jdbcTemplate.update("INSERT INTO mysql_compat(name) VALUES ('after-clean')");

            assertEquals(1L, jdbcTemplate.queryForObject(
                    "SELECT id FROM mysql_compat", Long.class));
        } finally {
            CaseExecutionContext.clear();
            dataSource.destroy();
        }
    }

    @Test
    void executesRegisteredAndRewrittenMySqlFunctions() {
        SmartTestRoutingDataSource dataSource = new SmartTestRoutingDataSource(URL);
        try {
            CaseExecutionContext.bind(new CaseContext("mysql-functions", "test/mysql-functions"));
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            SchemaInitializer.initialize(jdbcTemplate, "classpath:sql/schema-concurrent.sql");

            assertEquals(2, jdbcTemplate.queryForObject(
                    "SELECT FIND_IN_SET('b', 'a,b,c')", Integer.class));
            assertEquals("2024-03-05", jdbcTemplate.queryForObject(
                    SqlTextRewriter.rewriteDateFormatFunctions(
                            "SELECT DATE_FORMAT(TIMESTAMP '2024-03-05 12:30:00', '%Y-%m-%d')"),
                    String.class));
            assertEquals("2024-03-05", jdbcTemplate.queryForObject(
                    SqlTextRewriter.rewriteDateFormatFunctions(
                            "SELECT DATE_FORMAT('2024-03-05', '%Y-%m-%d')"),
                    String.class));
            assertEquals("yes", jdbcTemplate.queryForObject(
                    SqlTextRewriter.rewriteIfFunctions("SELECT IF(1 = 1, 'yes', 'no')"),
                    String.class));
            assertEquals("a-b", jdbcTemplate.queryForObject(
                    SqlTextRewriter.rewriteDoubleQuotedLiterals(
                            "SELECT REPLACE('a,b', \",\", \"-\")"),
                    String.class));
        } finally {
            CaseExecutionContext.clear();
            dataSource.destroy();
        }
    }
}

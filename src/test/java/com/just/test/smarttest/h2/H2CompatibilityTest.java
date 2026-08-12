package com.just.test.smarttest.h2;

import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.context.CaseExecutionContext;
import com.just.test.smarttest.datasource.SchemaInitializer;
import com.just.test.smarttest.datasource.SmartTestRoutingDataSource;
import com.just.test.smarttest.loader.DataSetLoader;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class H2CompatibilityTest {

    private static final String URL = "jdbc:h2:mem:{key};MODE=MySQL;DB_CLOSE_DELAY=-1;"
            + "DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";

    @Test
    void executesCleanedMySqlDdlOnH2() {
        SmartTestRoutingDataSource dataSource = new SmartTestRoutingDataSource(URL);
        try {
            CaseExecutionContext.bind(new CaseContext("mysql-ddl", "test/mysql-ddl"));
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            SchemaInitializer.initialize(jdbcTemplate, "classpath:sql/schema-mysql-compat.sql");

            jdbcTemplate.update("INSERT INTO mysql_compat(name) VALUES ('sample')");

            assertEquals(10L, jdbcTemplate.queryForObject(
                    "SELECT id FROM mysql_compat", Long.class));

            jdbcTemplate.update("DELETE FROM mysql_compat");
            DataSetLoader.cleanTables(jdbcTemplate);
            jdbcTemplate.update("INSERT INTO mysql_compat(name) VALUES ('after-delete')");

            assertEquals(10L, jdbcTemplate.queryForObject(
                    "SELECT id FROM mysql_compat", Long.class));
            assertEquals(false, jdbcTemplate.queryForObject(
                    "SELECT enabled FROM mysql_compat", Boolean.class));

            DataSetLoader.cleanTables(jdbcTemplate);
            jdbcTemplate.update("INSERT INTO mysql_compat(name) VALUES ('after-clean')");

            assertEquals(10L, jdbcTemplate.queryForObject(
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
                    "SELECT DATE_FORMAT(TIMESTAMP '2024-03-05 12:30:00', '%Y-%m-%d')",
                    String.class));
            assertEquals("2024-03-05", jdbcTemplate.queryForObject(
                    "SELECT DATE_FORMAT(DATE '2024-03-05', '%Y-%m-%d')",
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

package com.just.test.internal.h2;

import com.just.test.context.CaseContext;
import com.just.test.internal.context.CaseExecutionContext;
import com.just.test.internal.datasource.SchemaInitializer;
import com.just.test.internal.datasource.JustTestRoutingDataSource;
import com.just.test.internal.loader.DataSetLoader;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class H2CompatibilityTest {

    private static final String URL = "jdbc:h2:mem:{key};MODE=MySQL;"
            + "DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";

    @Test
    void executesCleanedMySqlDdlOnH2() {
        JustTestRoutingDataSource dataSource = new JustTestRoutingDataSource(URL);
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
        JustTestRoutingDataSource dataSource = new JustTestRoutingDataSource(URL);
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
            assertEquals("ab", jdbcTemplate.queryForObject(
                    SqlTextRewriter.rewriteDoubleQuotedLiterals(
                            "SELECT CONCAT(\"a\",\"b\")"),
                    String.class));
        } finally {
            CaseExecutionContext.clear();
            dataSource.destroy();
        }
    }

    @Test
    void formatsDateFormatLiteralsAndCommonSpecifiersOnH2() {
        JustTestRoutingDataSource dataSource = new JustTestRoutingDataSource(URL);
        try {
            CaseExecutionContext.bind(new CaseContext(
                    "mysql-date-format-literals", "test/mysql-date-format-literals"));
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            SchemaInitializer.initialize(jdbcTemplate, "classpath:sql/schema-concurrent.sql");

            assertEquals("Date: 2024", dateFormat(jdbcTemplate, "'Date: %Y'"));
            assertEquals("Day 05", dateFormat(jdbcTemplate, "'Day %d'"));
            assertEquals("2024-03-05 12:30:45", dateFormat(jdbcTemplate, "'%Y-%m-%d %H:%i:%s'"));
            assertEquals("2024年03月05日", dateFormat(jdbcTemplate, "'%Y年%m月%d日'"));
            assertEquals("12:30:45", dateFormat(jdbcTemplate, "'%T'"));
            assertEquals("%", dateFormat(jdbcTemplate, "'%%'"));
            assertEquals("It's 2024", dateFormat(jdbcTemplate, "'It''s %Y'"));
            assertEquals("2024-03-05 \"UTC\"", dateFormat(jdbcTemplate, "\"%Y-%m-%d \"\"UTC\"\"\""));
            assertEquals(DateTimeFormatter.ofPattern("MMMM").format(
                            LocalDateTime.of(2024, 3, 5, 12, 30, 45)),
                    dateFormat(jdbcTemplate, "'%M'"));
        } finally {
            CaseExecutionContext.clear();
            dataSource.destroy();
        }
    }

    private static String dateFormat(JdbcTemplate jdbcTemplate, String patternLiteral) {
        return jdbcTemplate.queryForObject(
                SqlTextRewriter.rewriteDateFormatFunctions(
                        "SELECT DATE_FORMAT(TIMESTAMP '2024-03-05 12:30:45', " + patternLiteral + ")"),
                String.class);
    }
}

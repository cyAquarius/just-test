package com.just.test.internal.datasource;

import com.just.test.context.CaseContext;
import com.just.test.internal.context.CaseExecutionContext;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchemaInitializerTest {

    private static final String URL = "jdbc:h2:mem:{key};MODE=MySQL;"
            + "DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";

    @Test
    void removesEscapedCommentQuotesCompletely() {
        assertEquals("col VARCHAR(32) NOT NULL", SchemaInitializer.cleanMySqlSyntax(
                "col VARCHAR(32) NOT NULL COMMENT 'it\\'s a flag'"));
        assertEquals("col VARCHAR(32) NOT NULL", SchemaInitializer.cleanMySqlSyntax(
                "col VARCHAR(32) NOT NULL COMMENT 'it''s a flag'"));
        assertEquals("col INT", SchemaInitializer.cleanMySqlSyntax(
                "col INT COMMENT 'it\\'s ''ok'''"));
        assertEquals(")", SchemaInitializer.cleanMySqlSyntax(
                ") COMMENT='owner''s table'"));
        assertEquals("enabled BIT(1) NOT NULL DEFAULT 0", SchemaInitializer.cleanMySqlSyntax(
                "enabled BIT(1) NOT NULL DEFAULT b'0' COMMENT 'it\\'s a flag'"));
    }

    @Test
    void keepsKeywordsInsideStringLiterals() {
        assertEquals("DEFAULT 'is UNSIGNED ok'", SchemaInitializer.cleanMySqlSyntax(
                "DEFAULT 'is UNSIGNED ok'"));
        assertEquals("DEFAULT \"is UNSIGNED ok\"", SchemaInitializer.cleanMySqlSyntax(
                "DEFAULT \"is UNSIGNED ok\""));
        assertEquals("DEFAULT 'ENGINE=InnoDB'", SchemaInitializer.cleanMySqlSyntax(
                "DEFAULT 'ENGINE=InnoDB'"));
        assertEquals("note VARCHAR(64) DEFAULT 'ON UPDATE CURRENT_TIMESTAMP'",
                SchemaInitializer.cleanMySqlSyntax(
                        "note VARCHAR(64) DEFAULT 'ON UPDATE CURRENT_TIMESTAMP'"));
    }

    @Test
    void keepsCollateTextInsideIdentifiersAndLiterals() {
        assertEquals("collate_flag VARCHAR(32) NOT NULL", SchemaInitializer.cleanMySqlSyntax(
                "collate_flag VARCHAR(32) NOT NULL"));
        assertEquals("`collate_flag` VARCHAR(32) NOT NULL", SchemaInitializer.cleanMySqlSyntax(
                "`collate_flag` VARCHAR(32) NOT NULL"));
        assertEquals("DEFAULT 'collate utf8'", SchemaInitializer.cleanMySqlSyntax(
                "DEFAULT 'collate utf8'"));
        assertEquals("name VARCHAR(64) NOT NULL", SchemaInitializer.cleanMySqlSyntax(
                "name VARCHAR(64) COLLATE utf8mb4_unicode_ci NOT NULL"));
        assertEquals(")", SchemaInitializer.cleanMySqlSyntax(
                ") COLLATE=utf8mb4_unicode_ci"));
        assertEquals(")", SchemaInitializer.cleanMySqlSyntax(
                ") COLLATE = utf8mb4_bin"));
        assertEquals("", SchemaInitializer.cleanMySqlSyntax(
                "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci").trim());
        assertEquals("DEFAULT", SchemaInitializer.cleanMySqlSyntax(
                "DEFAULT COLLATE utf8mb4_bin").trim());
    }

    @Test
    void keepsKeywordsInsideComments() {
        assertEquals("/* UNSIGNED COLLATE utf8 */ id INT", SchemaInitializer.cleanMySqlSyntax(
                "/* UNSIGNED COLLATE utf8 */ id INT"));
        assertEquals("-- UNSIGNED COLLATE utf8\nid INT", SchemaInitializer.cleanMySqlSyntax(
                "-- UNSIGNED COLLATE utf8\nid INT"));
        assertEquals("# ENGINE=InnoDB\nid INT", SchemaInitializer.cleanMySqlSyntax(
                "# ENGINE=InnoDB\nid INT"));
    }

    @Test
    void normalizesMySqlBackslashEscapesInSingleQuotedLiterals() {
        assertEquals("note VARCHAR(64) NOT NULL DEFAULT 'it''s ok'",
                SchemaInitializer.cleanMySqlSyntax(
                        "note VARCHAR(64) NOT NULL DEFAULT 'it\\'s ok' COMMENT 'it\\'s a flag'"));
        assertEquals("DEFAULT 'a\\b'", SchemaInitializer.cleanMySqlSyntax("DEFAULT 'a\\\\b'"));
        assertEquals("DEFAULT 'it''s ok'", SchemaInitializer.cleanMySqlSyntax("DEFAULT 'it''s ok'"));
        assertEquals("DEFAULT 'it'''", SchemaInitializer.cleanMySqlSyntax("DEFAULT 'it\\''"));
        assertEquals("DEFAULT 'line\nbreak'", SchemaInitializer.cleanMySqlSyntax("DEFAULT 'line\\nbreak'"));
        assertEquals("DEFAULT 'tab\there'", SchemaInitializer.cleanMySqlSyntax("DEFAULT 'tab\\there'"));
        assertEquals("DEFAULT 'say \"hi\"'", SchemaInitializer.cleanMySqlSyntax("DEFAULT 'say \\\"hi\\\"'"));
        assertEquals("DEFAULT '100%_x'", SchemaInitializer.cleanMySqlSyntax("DEFAULT '100\\%\\_\\x'"));
        assertEquals("DEFAULT \"it\\'s ok\"", SchemaInitializer.cleanMySqlSyntax("DEFAULT \"it\\'s ok\""));
        assertEquals("`it\\'s`", SchemaInitializer.cleanMySqlSyntax("`it\\'s`"));
        assertEquals("/* 'it\\'s' */ id INT", SchemaInitializer.cleanMySqlSyntax("/* 'it\\'s' */ id INT"));
    }

    @Test
    void createsTableFromMysqldumpDefaultsWithBackslashEscapes() {
        JustTestRoutingDataSource dataSource = new JustTestRoutingDataSource(URL);
        try {
            CaseExecutionContext.bind(new CaseContext(
                    "escaped-default", "test/escaped-default"));
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            SchemaInitializer.initialize(jdbcTemplate, "classpath:sql/schema-escaped-default.sql");

            jdbcTemplate.update("INSERT INTO exported_default(id) VALUES (1)");

            assertEquals("it's ok", jdbcTemplate.queryForObject(
                    "SELECT note FROM exported_default", String.class));
            assertEquals("a\\b", jdbcTemplate.queryForObject(
                    "SELECT path FROM exported_default", String.class));
            assertEquals("it's ok", jdbcTemplate.queryForObject(
                    "SELECT doubled FROM exported_default", String.class));
        } finally {
            CaseExecutionContext.clear();
            dataSource.destroy();
        }
    }

    @Test
    void createsTableFromExportedDdlWithEscapedComments() {
        JustTestRoutingDataSource dataSource = new JustTestRoutingDataSource(URL);
        try {
            CaseExecutionContext.bind(new CaseContext(
                    "escaped-comment", "test/escaped-comment"));
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            SchemaInitializer.initialize(jdbcTemplate, "classpath:sql/schema-escaped-comment.sql");

            jdbcTemplate.update("INSERT INTO exported_flag(id) VALUES (1)");

            assertEquals("is UNSIGNED ok", jdbcTemplate.queryForObject(
                    "SELECT collate_flag FROM exported_flag", String.class));
            assertEquals("collate utf8", jdbcTemplate.queryForObject(
                    "SELECT doubled_quote FROM exported_flag", String.class));
            assertEquals(false, jdbcTemplate.queryForObject(
                    "SELECT enabled FROM exported_flag", Boolean.class));
        } finally {
            CaseExecutionContext.clear();
            dataSource.destroy();
        }
    }
}

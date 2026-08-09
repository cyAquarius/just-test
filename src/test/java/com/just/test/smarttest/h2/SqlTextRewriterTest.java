package com.just.test.smarttest.h2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlTextRewriterTest {

    @Test
    void rewritesIfFunctionOnlyInExecutableSql() {
        String sql = "SELECT IF(enabled, 'IF(value)', 0), \"IF(identifier)\", `IF(column)` "
                + "FROM sample /* IF(comment) */ -- IF(line)\nWHERE id = 1";

        assertEquals(
                "SELECT CASEWHEN(enabled, 'IF(value)', 0), \"IF(identifier)\", `IF(column)` "
                        + "FROM sample /* IF(comment) */ -- IF(line)\nWHERE id = 1",
                SqlTextRewriter.rewriteIfFunctions(sql));
    }

    @Test
    void rewritesDoubleQuotedLiteralsOnlyInsideSupportedFunctions() {
        String sql = "SELECT \"quoted_column\", REPLACE(name, \",\", \"O'Reilly\"), \"outside\" FROM sample";

        assertEquals(
                "SELECT \"quoted_column\", REPLACE(name, ',', 'O''Reilly'), \"outside\" FROM sample",
                SqlTextRewriter.rewriteDoubleQuotedLiterals(sql));
    }

    @Test
    void preservesQuotedTextInComments() {
        String sql = "SELECT REPLACE(name, \"a\", \"b\") /* REPLACE(\"x\", \"y\") */";

        assertEquals("SELECT REPLACE(name, 'a', 'b') /* REPLACE(\"x\", \"y\") */",
                SqlTextRewriter.rewriteDoubleQuotedLiterals(sql));
    }
}

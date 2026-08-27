package com.just.test.smarttest.h2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SqlTextRewriterTest {

    @Test
    void returnsOriginalSqlWhenRewriteTokenIsAbsent() {
        String withoutIf = "SELECT enabled FROM sample";
        String withoutDateFormat = "SELECT create_time FROM sample";
        String withoutDoubleQuote = "SELECT name FROM sample";

        assertSame(withoutIf, SqlTextRewriter.rewriteIfFunctions(withoutIf));
        assertSame(withoutDateFormat, SqlTextRewriter.rewriteDateFormatFunctions(withoutDateFormat));
        assertSame(withoutDoubleQuote, SqlTextRewriter.rewriteDoubleQuotedLiterals(withoutDoubleQuote));
    }

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
    void rewritesSpacedIfFunctionAndPreservesWhitespace() {
        String sql = "SELECT IF (enabled, 'yes', 'no') FROM sample";

        assertEquals(
                "SELECT CASEWHEN (enabled, 'yes', 'no') FROM sample",
                SqlTextRewriter.rewriteIfFunctions(sql));
    }

    @Test
    void rewritesDateFormatFunctionCaseInsensitivelyAndPreservesWhitespace() {
        String sql = "SELECT DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s'), "
                + "date_format (updated_at, '%y/%m/%d %h:%i:%S') FROM sample";

        assertEquals(
                "SELECT FORMATDATETIME(create_time, 'yyyy-MM-dd HH:mm:ss'), "
                        + "FORMATDATETIME (updated_at, 'yy/MM/dd hh:mm:ss') FROM sample",
                SqlTextRewriter.rewriteDateFormatFunctions(sql));
    }

    @Test
    void rewritesDoubleQuotedDateFormatPatternToSingleQuotedJavaPattern() {
        String sql = "SELECT DATE_FORMAT(create_time, \"%Y-%m-%d\") FROM sample";

        assertEquals(
                "SELECT FORMATDATETIME(create_time, 'yyyy-MM-dd') FROM sample",
                SqlTextRewriter.rewriteDateFormatFunctions(sql));
    }

    @Test
    void unescapesDoubleQuotedDateFormatPatternQuotes() {
        String sql = "SELECT DATE_FORMAT(create_time, \"%Y-%m-%d \"\"UTC\"\"\") FROM sample";

        assertEquals(
                "SELECT FORMATDATETIME(create_time, 'yyyy-MM-dd \"UTC\"') FROM sample",
                SqlTextRewriter.rewriteDateFormatFunctions(sql));
    }

    @Test
    void doesNotRewriteDateFormatTextInsideQuotesOrComments() {
        String sql = "SELECT 'DATE_FORMAT(create_time, ''%Y-%m-%d'')', "
                + "\"DATE_FORMAT(identifier, '%Y-%m-%d')\", `DATE_FORMAT(column, '%Y-%m-%d')` "
                + "/* DATE_FORMAT(comment, '%Y-%m-%d') */ -- DATE_FORMAT(line, '%Y-%m-%d')\n"
                + "FROM sample";

        assertEquals(sql, SqlTextRewriter.rewriteDateFormatFunctions(sql));
    }

    @Test
    void preservesNestedExpressionAsDateFormatFirstArgument() {
        String sql = "SELECT DATE_FORMAT(COALESCE(create_time, fallback_time), '%Y-%m-%d') "
                + "FROM sample";

        assertEquals(
                "SELECT FORMATDATETIME(COALESCE(create_time, fallback_time), 'yyyy-MM-dd') "
                        + "FROM sample",
                SqlTextRewriter.rewriteDateFormatFunctions(sql));
    }

    @Test
    void preservesDateFormatCallWithoutTopLevelComma() {
        String sql = "SELECT DATE_FORMAT(expr) FROM sample";

        assertEquals(sql, SqlTextRewriter.rewriteDateFormatFunctions(sql));
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

    @Test
    void rewritesDoubleQuotedLiteralOnRightSideOfComparisonOnly() {
        String sql = "SELECT \"quoted_column\" FROM sample WHERE description != \"historical\" "
                + "AND category = \"A\"";

        assertEquals("SELECT \"quoted_column\" FROM sample WHERE description != 'historical' "
                        + "AND category = 'A'",
                SqlTextRewriter.rewriteDoubleQuotedLiterals(sql));
    }
}

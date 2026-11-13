package com.just.test.internal.h2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                "SELECT FORMATDATETIME(create_time, 'yyyy-MM-dd'' \"UTC\"''') FROM sample",
                SqlTextRewriter.rewriteDateFormatFunctions(sql));
    }

    @Test
    void quotesAsciiLiteralTextInDateFormatPattern() {
        String sql = "SELECT DATE_FORMAT(created_at, 'Date: %Y'), DATE_FORMAT(created_at, 'Day %d') "
                + "FROM sample";

        assertEquals(
                "SELECT FORMATDATETIME(created_at, '''Date: ''yyyy'), "
                        + "FORMATDATETIME(created_at, '''Day ''dd') FROM sample",
                SqlTextRewriter.rewriteDateFormatFunctions(sql));
    }

    @Test
    void rewritesMonthNameTimeAndEscapedPercent() {
        String sql = "SELECT DATE_FORMAT(created_at, '%M'), DATE_FORMAT(created_at, '%T'), "
                + "DATE_FORMAT(created_at, '%%'), DATE_FORMAT(created_at, '%% done') FROM sample";

        assertEquals(
                "SELECT FORMATDATETIME(created_at, 'MMMM'), "
                        + "FORMATDATETIME(created_at, 'HH:mm:ss'), "
                        + "FORMATDATETIME(created_at, '%'), "
                        + "FORMATDATETIME(created_at, '''% done''') FROM sample",
                SqlTextRewriter.rewriteDateFormatFunctions(sql));
    }

    @Test
    void keepsNumericAndCjkDateFormatPatternsUnquoted() {
        String sql = "SELECT DATE_FORMAT(created_at, '%Y年%m月%d日') FROM sample";

        assertEquals(
                "SELECT FORMATDATETIME(created_at, 'yyyy年MM月dd日') FROM sample",
                SqlTextRewriter.rewriteDateFormatFunctions(sql));
    }

    @Test
    void doublesApostrophesInsideDateFormatLiterals() {
        String sql = "SELECT DATE_FORMAT(created_at, 'It''s %Y') FROM sample";

        assertEquals(
                "SELECT FORMATDATETIME(created_at, '''It''''s ''yyyy') FROM sample",
                SqlTextRewriter.rewriteDateFormatFunctions(sql));
    }

    @Test
    void rejectsUnknownDateFormatSpecifierWithOriginalFormat() {
        String format = "Date: %Q";
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                SqlTextRewriter.rewriteDateFormatFunctions(
                        "SELECT DATE_FORMAT(created_at, '" + format + "') FROM sample"));

        assertTrue(error.getMessage().contains(format));
        assertTrue(error.getMessage().contains("%Q"));

        IllegalArgumentException quoted = assertThrows(IllegalArgumentException.class, () ->
                SqlTextRewriter.rewriteDateFormatFunctions(
                        "SELECT DATE_FORMAT(created_at, 'It''s %Q') FROM sample"));
        assertTrue(quoted.getMessage().contains("It's %Q"));
    }

    @Test
    void rejectsTrailingPercentInDateFormatPattern() {
        String format = "100%";
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                SqlTextRewriter.rewriteDateFormatFunctions(
                        "SELECT DATE_FORMAT(created_at, '" + format + "') FROM sample"));

        assertTrue(error.getMessage().contains(format));
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
    void rewritesDoubleQuotedLiteralsInsideConcat() {
        String sql = "SELECT CONCAT(\"a\",\"b\"), concat(\"x\",\"y\"), \"quoted_column\" FROM sample";

        assertEquals(
                "SELECT CONCAT('a','b'), concat('x','y'), \"quoted_column\" FROM sample",
                SqlTextRewriter.rewriteDoubleQuotedLiterals(sql));
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

package com.just.test.internal.h2;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** SQL 文本的轻量级词法改写，只处理字符串、标识符和注释之外的函数调用。 */
final class SqlTextRewriter {

    private static final Set<String> STRING_FUNCTIONS = new HashSet<>(Arrays.asList(
            "REPLACE", "CONCAT_WS", "INSERT", "LOCATE", "INSTR", "POSITION", "SUBSTRING_INDEX"));

    private SqlTextRewriter() {
    }

    static String rewriteIfFunctions(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }
        if (!containsFunctionIgnoreCase(sql, "IF")) {
            return sql;
        }
        StringBuilder result = new StringBuilder(sql.length());
        int index = 0;
        while (index < sql.length()) {
            char current = sql.charAt(index);
            if (current == '\'' || current == '"' || current == '`') {
                index = appendQuoted(sql, index, current, result, false);
            } else if (startsLineComment(sql, index)) {
                index = appendLineComment(sql, index, result);
            } else if (startsBlockComment(sql, index)) {
                index = appendBlockComment(sql, index, result);
            } else if (matchesFunction(sql, index, "IF")) {
                result.append("CASEWHEN");
                index += 2;
            } else {
                result.append(current);
                index++;
            }
        }
        return result.toString();
    }

    static String rewriteDateFormatFunctions(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }
        if (!containsIgnoreCase(sql, "DATE_FORMAT")) {
            return sql;
        }
        StringBuilder result = new StringBuilder(sql.length());
        int index = 0;
        while (index < sql.length()) {
            char current = sql.charAt(index);
            if (current == '\'' || current == '"' || current == '`') {
                index = appendQuoted(sql, index, current, result, false);
            } else if (startsLineComment(sql, index)) {
                index = appendLineComment(sql, index, result);
            } else if (startsBlockComment(sql, index)) {
                index = appendBlockComment(sql, index, result);
            } else if (matchesFunction(sql, index, "DATE_FORMAT")) {
                int nextIndex = appendDateFormatFunction(sql, index, result);
                if (nextIndex > index) {
                    index = nextIndex;
                } else {
                    index = appendUnchangedDateFormatFunction(sql, index, result);
                }
            } else {
                result.append(current);
                index++;
            }
        }
        return result.toString();
    }

    static String rewriteDoubleQuotedLiterals(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }
        if (!containsIgnoreCase(sql, "\"")) {
            return sql;
        }
        StringBuilder result = new StringBuilder(sql.length());
        Deque<Boolean> stringFunctionScopes = new ArrayDeque<>();
        boolean pendingStringFunction = false;
        int index = 0;
        while (index < sql.length()) {
            char current = sql.charAt(index);
            boolean inStringFunction = !stringFunctionScopes.isEmpty() && stringFunctionScopes.peek();
            if (current == '\'') {
                index = appendQuoted(sql, index, current, result, false);
                pendingStringFunction = false;
            } else if (current == '`') {
                index = appendQuoted(sql, index, current, result, false);
                pendingStringFunction = false;
            } else if (current == '"') {
                index = appendQuoted(sql, index, current, result,
                        inStringFunction || followsComparisonOperator(sql, index));
                pendingStringFunction = false;
            } else if (startsLineComment(sql, index)) {
                index = appendLineComment(sql, index, result);
            } else if (startsBlockComment(sql, index)) {
                index = appendBlockComment(sql, index, result);
            } else if (isIdentifierStart(current)) {
                int end = index + 1;
                while (end < sql.length() && isIdentifierPart(sql.charAt(end))) {
                    end++;
                }
                String identifier = sql.substring(index, end);
                result.append(identifier);
                pendingStringFunction = STRING_FUNCTIONS.contains(identifier.toUpperCase(Locale.ROOT));
                index = end;
            } else if (current == '(') {
                boolean active = inStringFunction || pendingStringFunction;
                stringFunctionScopes.push(active);
                result.append(current);
                pendingStringFunction = false;
                index++;
            } else if (current == ')') {
                if (!stringFunctionScopes.isEmpty()) {
                    stringFunctionScopes.pop();
                }
                result.append(current);
                pendingStringFunction = false;
                index++;
            } else {
                result.append(current);
                if (!Character.isWhitespace(current)) {
                    pendingStringFunction = false;
                }
                index++;
            }
        }
        return result.toString();
    }

    private static int appendDateFormatFunction(String sql, int functionIndex, StringBuilder result) {
        int functionEnd = functionIndex + "DATE_FORMAT".length();
        int openParenthesis = skipWhitespace(sql, functionEnd, sql.length());
        if (openParenthesis >= sql.length() || sql.charAt(openParenthesis) != '(') {
            return -1;
        }

        int closeParenthesis = findMatchingParenthesis(sql, openParenthesis);
        if (closeParenthesis < 0) {
            return -1;
        }
        int comma = findTopLevelComma(sql, openParenthesis + 1, closeParenthesis);
        if (comma < 0) {
            return -1;
        }

        int firstArgumentStart = skipWhitespace(sql, openParenthesis + 1, comma);
        int firstArgumentEnd = trimWhitespace(sql, firstArgumentStart, comma);
        int formatStart = skipWhitespace(sql, comma + 1, closeParenthesis);
        int formatEnd = trimWhitespace(sql, formatStart, closeParenthesis);

        result.append("FORMATDATETIME");
        result.append(sql, functionEnd, openParenthesis + 1);
        if (isSingleQuotedLiteral(sql, firstArgumentStart, firstArgumentEnd)) {
            result.append(sql, openParenthesis + 1, firstArgumentStart);
            result.append("CAST(");
            result.append(sql, firstArgumentStart, firstArgumentEnd);
            result.append(" AS TIMESTAMP)");
            result.append(sql, firstArgumentEnd, comma);
        } else {
            result.append(sql, openParenthesis + 1, comma);
        }
        result.append(sql, comma, formatStart);
        if (isSingleQuotedLiteral(sql, formatStart, formatEnd)) {
            result.append('\'');
            result.append(rewriteDateFormatPattern(sql.substring(formatStart + 1, formatEnd - 1)));
            result.append('\'');
        } else if (isQuotedLiteral(sql, formatStart, formatEnd, '"')) {
            result.append('\'');
            appendSqlStringLiteral(result, rewriteDateFormatPattern(
                    unescapeQuotedLiteral(sql, formatStart, formatEnd, '"')));
            result.append('\'');
        } else {
            result.append(sql, formatStart, formatEnd);
        }
        result.append(sql, formatEnd, closeParenthesis + 1);
        return closeParenthesis + 1;
    }

    private static int appendUnchangedDateFormatFunction(String sql, int functionIndex,
                                                         StringBuilder result) {
        int functionEnd = functionIndex + "DATE_FORMAT".length();
        int openParenthesis = skipWhitespace(sql, functionEnd, sql.length());
        if (openParenthesis < sql.length() && sql.charAt(openParenthesis) == '(') {
            int closeParenthesis = findMatchingParenthesis(sql, openParenthesis);
            if (closeParenthesis >= 0) {
                result.append(sql, functionIndex, closeParenthesis + 1);
                return closeParenthesis + 1;
            }
        }
        result.append(sql, functionIndex, sql.length());
        return sql.length();
    }

    private static int findMatchingParenthesis(String sql, int openParenthesis) {
        int depth = 1;
        int index = openParenthesis + 1;
        while (index < sql.length()) {
            char current = sql.charAt(index);
            if (current == '\'' || current == '"' || current == '`') {
                index = skipQuoted(sql, index, current);
            } else if (startsLineComment(sql, index)) {
                index = skipLineComment(sql, index);
            } else if (startsBlockComment(sql, index)) {
                index = skipBlockComment(sql, index);
            } else if (current == '(') {
                depth++;
                index++;
            } else if (current == ')') {
                depth--;
                if (depth == 0) {
                    return index;
                }
                index++;
            } else {
                index++;
            }
        }
        return -1;
    }

    private static int findTopLevelComma(String sql, int start, int end) {
        int depth = 0;
        int index = start;
        while (index < end) {
            char current = sql.charAt(index);
            if (current == '\'' || current == '"' || current == '`') {
                index = skipQuoted(sql, index, current);
            } else if (startsLineComment(sql, index)) {
                index = skipLineComment(sql, index);
            } else if (startsBlockComment(sql, index)) {
                index = skipBlockComment(sql, index);
            } else if (current == '(') {
                depth++;
                index++;
            } else if (current == ')') {
                depth--;
                index++;
            } else if (current == ',' && depth == 0) {
                return index;
            } else {
                index++;
            }
        }
        return -1;
    }

    private static int skipWhitespace(String sql, int start, int end) {
        int index = start;
        while (index < end && Character.isWhitespace(sql.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int trimWhitespace(String sql, int start, int end) {
        int index = end;
        while (index > start && Character.isWhitespace(sql.charAt(index - 1))) {
            index--;
        }
        return index;
    }

    private static boolean isSingleQuotedLiteral(String sql, int start, int end) {
        return isQuotedLiteral(sql, start, end, '\'');
    }

    private static boolean isQuotedLiteral(String sql, int start, int end, char quote) {
        return start < end && sql.charAt(start) == quote
                && skipQuoted(sql, start, quote) == end;
    }

    private static String unescapeQuotedLiteral(String sql, int start, int end, char quote) {
        StringBuilder result = new StringBuilder(end - start - 2);
        int index = start + 1;
        while (index < end - 1) {
            char current = sql.charAt(index);
            if (current == quote && index + 1 < end - 1
                    && sql.charAt(index + 1) == quote) {
                result.append(quote);
                index += 2;
            } else if (current == '\\' && index + 1 < end - 1
                    && sql.charAt(index + 1) == quote) {
                result.append(quote);
                index += 2;
            } else {
                result.append(current);
                index++;
            }
        }
        return result.toString();
    }

    private static void appendSqlStringLiteral(StringBuilder result, String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '\'') {
                result.append("''");
            } else {
                result.append(current);
            }
        }
    }

    private static int skipQuoted(String sql, int index, char quote) {
        index++;
        while (index < sql.length()) {
            if (sql.charAt(index) == '\\' && index + 1 < sql.length()) {
                index += 2;
                continue;
            }
            if (sql.charAt(index) == quote) {
                if (index + 1 < sql.length() && sql.charAt(index + 1) == quote) {
                    index += 2;
                    continue;
                }
                return index + 1;
            }
            index++;
        }
        return index;
    }

    private static int skipLineComment(String sql, int index) {
        while (index < sql.length()) {
            char current = sql.charAt(index++);
            if (current == '\n' || current == '\r') {
                break;
            }
        }
        return index;
    }

    private static int skipBlockComment(String sql, int index) {
        index += 2;
        while (index < sql.length()) {
            if (index + 1 < sql.length() && sql.charAt(index) == '*' && sql.charAt(index + 1) == '/') {
                return index + 2;
            }
            index++;
        }
        return index;
    }

    private static String rewriteDateFormatPattern(String pattern) {
        StringBuilder result = new StringBuilder(pattern.length());
        int index = 0;
        while (index < pattern.length()) {
            char current = pattern.charAt(index);
            if (current == '%' && index + 1 < pattern.length()) {
                char specifier = pattern.charAt(index + 1);
                switch (specifier) {
                    case 'Y':
                        result.append("yyyy");
                        break;
                    case 'y':
                        result.append("yy");
                        break;
                    case 'm':
                        result.append("MM");
                        break;
                    case 'd':
                        result.append("dd");
                        break;
                    case 'H':
                        result.append("HH");
                        break;
                    case 'h':
                        result.append("hh");
                        break;
                    case 'i':
                        result.append("mm");
                        break;
                    case 's':
                    case 'S':
                        result.append("ss");
                        break;
                    default:
                        result.append(current).append(specifier);
                        break;
                }
                index += 2;
            } else {
                result.append(current);
                index++;
            }
        }
        return result.toString();
    }

    private static boolean containsIgnoreCase(String text, String token) {
        int limit = text.length() - token.length();
        for (int index = 0; index <= limit; index++) {
            if (text.regionMatches(true, index, token, 0, token.length())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsFunctionIgnoreCase(String text, String functionName) {
        int limit = text.length() - functionName.length();
        for (int index = 0; index <= limit; index++) {
            if (matchesFunction(text, index, functionName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesFunction(String sql, int index, String functionName) {
        int end = index + functionName.length();
        if (end > sql.length() || !sql.regionMatches(true, index, functionName, 0, functionName.length())) {
            return false;
        }
        if (index > 0 && isIdentifierPart(sql.charAt(index - 1))) {
            return false;
        }
        if (end < sql.length() && isIdentifierPart(sql.charAt(end))) {
            return false;
        }
        while (end < sql.length() && Character.isWhitespace(sql.charAt(end))) {
            end++;
        }
        return end < sql.length() && sql.charAt(end) == '(';
    }

    private static boolean followsComparisonOperator(String sql, int quoteIndex) {
        int index = quoteIndex - 1;
        while (index >= 0 && Character.isWhitespace(sql.charAt(index))) {
            index--;
        }
        if (index < 0) {
            return false;
        }
        char current = sql.charAt(index);
        if (current == '=') {
            return true;
        }
        return (current == '!' || current == '<' || current == '>')
                && index + 1 < quoteIndex;
    }

    private static int appendQuoted(String sql, int index, char quote,
                                    StringBuilder result, boolean convertDoubleQuote) {
        result.append(convertDoubleQuote ? '\'' : quote);
        index++;
        while (index < sql.length()) {
            char current = sql.charAt(index);
            if (current == quote) {
                if (index + 1 < sql.length() && sql.charAt(index + 1) == quote) {
                    if (convertDoubleQuote) {
                        result.append(quote);
                    } else {
                        result.append(quote).append(quote);
                    }
                    index += 2;
                    continue;
                }
                result.append(convertDoubleQuote ? '\'' : quote);
                return index + 1;
            }
            if (convertDoubleQuote && current == '\'') {
                result.append("''");
            } else {
                result.append(current);
            }
            index++;
        }
        return index;
    }

    private static boolean startsLineComment(String sql, int index) {
        return sql.charAt(index) == '#'
                || (sql.charAt(index) == '-' && index + 1 < sql.length() && sql.charAt(index + 1) == '-');
    }

    private static boolean startsBlockComment(String sql, int index) {
        return sql.charAt(index) == '/' && index + 1 < sql.length() && sql.charAt(index + 1) == '*';
    }

    private static int appendLineComment(String sql, int index, StringBuilder result) {
        while (index < sql.length()) {
            char current = sql.charAt(index++);
            result.append(current);
            if (current == '\n' || current == '\r') {
                break;
            }
        }
        return index;
    }

    private static int appendBlockComment(String sql, int index, StringBuilder result) {
        result.append("/*");
        index += 2;
        while (index < sql.length()) {
            if (index + 1 < sql.length() && sql.charAt(index) == '*' && sql.charAt(index + 1) == '/') {
                result.append("*/");
                return index + 2;
            }
            result.append(sql.charAt(index++));
        }
        return index;
    }

    private static boolean isIdentifierStart(char value) {
        return Character.isLetter(value) || value == '_';
    }

    private static boolean isIdentifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$';
    }
}

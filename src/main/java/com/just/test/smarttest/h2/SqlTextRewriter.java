package com.just.test.smarttest.h2;

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

    static String rewriteDoubleQuotedLiterals(String sql) {
        if (sql == null || sql.isEmpty()) {
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

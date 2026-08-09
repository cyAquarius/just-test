package com.just.test.smarttest.matcher;

import com.alibaba.fastjson2.JSON;

import java.util.regex.Pattern;

/**
 * ACTS 2.0 风格 Flag 体系。通过字段名后缀 bracket flag 控制字段角色。
 *
 * <h3>Flag 列表</h3>
 * <table>
 *   <tr><th>Flag</th><th>含义</th><th>阶段</th><th>示例</th></tr>
 *   <tr><td>C</td><td>Condition — WHERE 定位条件</td><td>expect/clean</td><td>{@code id[C]: 1}</td></tr>
 *   <tr><td>Y</td><td>Yes — 精确断言（默认，无需显式标记）</td><td>expect</td><td>{@code balance: "0.00"}</td></tr>
 *   <tr><td>N</td><td>No — 跳过验证</td><td>expect/prepare</td><td>{@code add_time[N]:}</td></tr>
 *   <tr><td>CN</td><td>Condition + Not exist — 定位后断言行不存在</td><td>expect</td><td>{@code id[CN]: 1}</td></tr>
 *   <tr><td>R</td><td>Regex — 正则匹配</td><td>expect</td><td>{@code name[R]: "张.*"}</td></tr>
 *   <tr><td>A</td><td>Available — 非空断言</td><td>expect</td><td>{@code create_by[A]:}</td></tr>
 *   <tr><td>D</td><td>Date — 日期容差比较（秒）</td><td>expect</td><td>{@code gmt_create[D60]:} 60秒内</td></tr>
 *   <tr><td>J</td><td>JSON — JSON 结构比较</td><td>expect</td><td>{@code ext_info[J]: '{"k":"v"}'}</td></tr>
 *   <tr><td>F</td><td>Function — DB 函数（prepare 阶段插入）</td><td>prepare</td><td>{@code gmt_create[F]: "NOW()"}</td></tr>
 * </table>
 */
public final class BuiltInMatchers {

    /** Condition — WHERE 定位条件 */
    public static final String FLAG_C = "C";
    /** No — 跳过 */
    public static final String FLAG_N = "N";
    /** Condition + Not exist */
    public static final String FLAG_CN = "CN";
    /** Regex — 正则匹配 */
    public static final String FLAG_R = "R";
    /** Available — 非空断言 */
    public static final String FLAG_A = "A";
    /** Date — 日期容差比较 */
    public static final String FLAG_D = "D";
    /** JSON — JSON 结构比较 */
    public static final String FLAG_J = "J";
    /** Function — DB 函数 */
    public static final String FLAG_F = "F";

    private BuiltInMatchers() {
    }

    // ==================== Bracket Flag 解析 ====================

    /**
     * 判断字段名是否包含 bracket flag，如 "id[C]"、"add_time[N]"。
     */
    static boolean hasBracketFlag(String fieldName) {
        return fieldName != null && fieldName.endsWith("]") && fieldName.contains("[");
    }

    /**
     * 提取真实字段名（去掉 bracket flag）。如 "id[C]" → "id"。
     */
    public static String extractFieldName(String fieldName) {
        if (!hasBracketFlag(fieldName)) {
            return fieldName;
        }
        return fieldName.substring(0, fieldName.indexOf('['));
    }

    /**
     * 提取 bracket flag。如 "id[C]" → "C"，"gmt_create[D60]" → "D60"。
     */
    public static String extractFlag(String fieldName) {
        if (!hasBracketFlag(fieldName)) {
            return null;
        }
        return fieldName.substring(fieldName.indexOf('[') + 1, fieldName.length() - 1);
    }

    /**
     * 判断 flag 是否为 D（日期容差）类型。支持 "D" 和 "D60" 格式。
     */
    public static boolean isDateFlag(String flag) {
        return flag != null && flag.startsWith(FLAG_D)
                && (flag.length() == 1 || isNumeric(flag.substring(1)));
    }

    /**
     * 从 D flag 中提取容差秒数。"D" → 60（默认），"D120" → 120。
     */
    public static int extractDateTolerance(String flag) {
        if (flag == null || !flag.startsWith(FLAG_D)) {
            return 60;
        }
        if (flag.length() == 1) {
            return 60; // 默认 60 秒
        }
        return Integer.parseInt(flag.substring(1));
    }

    // ==================== 统一 Flag 断言 ====================

    /**
     * 对 matcher 类 flag（A/R/D/J）执行断言，消除 DataSetVerifier 与 ResultVerifier 的重复分发逻辑。
     *
     * <p>处理的 flag：A（非空）、R（正则）、D/Dxx（日期容差）、J（JSON 结构）。
     * 不处理 C/CN/N 和默认精确匹配（Y），由调用方自行处理。</p>
     *
     * @param flag          bracket flag（如 "A"、"R"、"D60"、"J"），null 表示无 flag
     * @param expectedValue YAML 中的期望值
     * @param actualValue   实际值（DB 字段值或返回值字段）
     * @param fieldPath     字段路径，用于错误信息（如 "table row[0].col" 或 "result.field"）
     * @return {@code null} 表示该 flag 不由本方法处理（调用方需继续分发）；
     *         空字符串表示已处理且验证通过；非空字符串表示验证失败信息
     */
    public static String assertMatcherFlag(String flag, Object expectedValue, Object actualValue, String fieldPath) {
        if (FLAG_A.equals(flag)) {
            return actualValue == null
                    ? String.format("[%s]: expected not null but got null", fieldPath)
                    : "";
        }

        if (FLAG_R.equals(flag)) {
            String pattern = expectedValue == null ? "" : expectedValue.toString();
            FieldMatcher matcher = regexMatcher(pattern);
            return matcher.matches(actualValue) ? ""
                    : String.format("[%s]: expected %s but got <%s>", fieldPath, matcher.describe(), actualValue);
        }

        if (flag != null && isDateFlag(flag)) {
            int tolerance = extractDateTolerance(flag);
            FieldMatcher matcher = dateMatcher(tolerance);
            return matcher.matches(actualValue) ? ""
                    : String.format("[%s]: expected %s but got <%s>", fieldPath, matcher.describe(), actualValue);
        }

        if (FLAG_J.equals(flag)) {
            String expectedJson = expectedValue == null ? null : expectedValue.toString();
            FieldMatcher matcher = jsonMatcher(expectedJson);
            return matcher.matches(actualValue) ? ""
                    : String.format("[%s]: expected %s but got <%s>", fieldPath, matcher.describe(), actualValue);
        }

        return null; // 非 matcher flag，调用方自行处理
    }

    // ==================== 匹配器工厂 ====================

    /**
     * 创建正则匹配器。
     */
    public static FieldMatcher regexMatcher(String pattern) {
        return new RegexMatcher(pattern);
    }

    /**
     * 创建 JSON 比较匹配器。
     */
    public static FieldMatcher jsonMatcher(String expectedJson) {
        return new JsonMatcher(expectedJson);
    }

    /**
     * 创建日期容差匹配器。
     */
    public static FieldMatcher dateMatcher(int toleranceSeconds) {
        return new DateMatcher(toleranceSeconds);
    }

    // ==================== 匹配器实现 ====================

    private static boolean isNumeric(String str) {
        for (char c : str.toCharArray()) {
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return !str.isEmpty();
    }

    static class RegexMatcher implements FieldMatcher {
        private final Pattern pattern;

        RegexMatcher(String regex) {
            try {
                this.pattern = Pattern.compile(regex);
            } catch (java.util.regex.PatternSyntaxException e) {
                throw new IllegalArgumentException(
                        "[SmartTest] Invalid regex in [R] flag: " + regex, e);
            }
        }

        @Override
        public boolean matches(Object actualValue) {
            if (actualValue == null) {
                return false;
            }
            return pattern.matcher(actualValue.toString()).matches();
        }

        @Override
        public String describe() {
            return "[R](" + pattern.pattern() + ")";
        }
    }

    static class DateMatcher implements FieldMatcher {
        private final int toleranceSeconds;

        DateMatcher(int toleranceSeconds) {
            this.toleranceSeconds = toleranceSeconds;
        }

        @Override
        public boolean matches(Object actualValue) {
            if (actualValue == null) {
                return false;
            }
            long actualMillis;
            if (actualValue instanceof java.sql.Timestamp) {
                actualMillis = ((java.sql.Timestamp) actualValue).getTime();
            } else if (actualValue instanceof java.util.Date) {
                actualMillis = ((java.util.Date) actualValue).getTime();
            } else {
                // 尝试解析字符串
                try {
                    actualMillis = java.sql.Timestamp.valueOf(actualValue.toString()).getTime();
                } catch (IllegalArgumentException e) {
                    return false;
                }
            }
            long diff = Math.abs(System.currentTimeMillis() - actualMillis);
            return diff <= (long) toleranceSeconds * 1000;
        }

        @Override
        public String describe() {
            return "[D" + toleranceSeconds + "]";
        }
    }

    static class JsonMatcher implements FieldMatcher {
        private final String expectedJson;

        JsonMatcher(String expectedJson) {
            this.expectedJson = expectedJson;
        }

        @Override
        public boolean matches(Object actualValue) {
            if (actualValue == null && expectedJson == null) {
                return true;
            }
            if (actualValue == null || expectedJson == null) {
                return false;
            }
            try {
                Object expected = JSON.parse(expectedJson);
                Object actual = JSON.parse(actualValue.toString());
                return expected.equals(actual);
            } catch (Exception e) {
                // JSON 解析失败，降级为字符串比较
                return expectedJson.equals(actualValue.toString());
            }
        }

        @Override
        public String describe() {
            return "[J](" + expectedJson + ")";
        }
    }
}

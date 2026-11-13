package com.just.test.internal.matcher;

import com.alibaba.fastjson2.JSON;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
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
 *   <tr><td>D</td><td>Date — 相对当前时刻的新鲜度（秒），YAML 期望值不比较</td><td>expect</td><td>{@code gmt_create[D60]:} 距现在 60 秒内</td></tr>
 *   <tr><td>J</td><td>JSON — JSON 结构比较；数值按 BigDecimal 归一后比较</td><td>expect</td><td>{@code ext_info[J]: '{"k":"v"}'}</td></tr>
 *   <tr><td>F</td><td>Function — DB 函数（prepare 阶段插入）</td><td>prepare</td><td>{@code gmt_create[F]: "NOW()"}</td></tr>
 * </table>
 *
 * <p>Flag 区分大小写。未知 flag、大小写错误（如 {@code [cn]}）或用在不适用阶段的 flag
 * 在解析时失败。错误信息包含字段名、case 路径和该阶段的合法 flag，不会降级为精确匹配。</p>
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
    /** Date — 相对当前时刻的新鲜度（秒）；YAML 期望值不参与比较 */
    public static final String FLAG_D = "D";
    /** JSON — JSON 结构比较 */
    public static final String FLAG_J = "J";
    /** Function — DB 函数 */
    public static final String FLAG_F = "F";

    /**
     * 一个 YAML 阶段允许的 bracket flag。
     * 不在该集合内的 flag（含大小写错误）在解析时失败，不会降级为精确匹配。
     */
    public enum FlagStage {
        /** {@code prepare.yaml}：{@code N}、{@code F} */
        PREPARE,
        /** {@code expect.yaml}：{@code C}、{@code CN}、{@code N}、{@code R}、{@code A}、{@code D}、{@code J} */
        EXPECT,
        /** {@code response.yaml}：{@code C}、{@code N}、{@code R}、{@code A}、{@code D}、{@code J} */
        RESPONSE,
        /** {@code expect_exception.yaml}：{@code N}、{@code R}、{@code A}、{@code D}、{@code J} */
        EXCEPTION
    }

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
        try {
            return Integer.parseInt(flag.substring(1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "[JustTest] Invalid date tolerance [" + flag + "]", e);
        }
    }

    // ==================== Flag 白名单 ====================

    /**
     * 校验节点里的全部 bracket flag。无 flag 的 key 跳过。
     * 未知、大小写不匹配或本阶段不适用的 flag 抛出 {@link IllegalArgumentException}。
     *
     * @param node         YAML 节点（Map、List 或标量）
     * @param stage        该阶段允许的 flag
     * @param caseLocation case 路径，建议含阶段文件名；没有路径时传 null
     */
    public static void requireKnownFlags(Object node, FlagStage stage, String caseLocation) {
        if (stage == null) {
            throw new IllegalArgumentException("[JustTest] Flag stage is required");
        }
        walkFlags(node, stage, caseLocation);
    }

    private static void walkFlags(Object node, FlagStage stage, String caseLocation) {
        if (node instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) node).entrySet()) {
                if (entry.getKey() instanceof String) {
                    requireKnownFlag((String) entry.getKey(), stage, caseLocation);
                }
                walkFlags(entry.getValue(), stage, caseLocation);
            }
            return;
        }
        if (node instanceof List) {
            for (Object item : (List<?>) node) {
                walkFlags(item, stage, caseLocation);
            }
        }
    }

    private static void requireKnownFlag(String rawKey, FlagStage stage, String caseLocation) {
        String flag = extractFlag(rawKey);
        if (flag == null) {
            return;
        }
        if (!isAllowed(flag, stage)) {
            throw new IllegalArgumentException(unknownFlagMessage(rawKey, flag, stage, caseLocation));
        }
        requireParsableDateTolerance(rawKey, flag, caseLocation);
    }

    /**
     * {@code [D60]} 的秒数必须能被 {@link Integer#parseInt} 接受。
     * 超出 int 范围时失败，并带上字段和 case，而不是裸 {@link NumberFormatException}。
     */
    private static void requireParsableDateTolerance(String rawKey, String flag, String caseLocation) {
        if (!isDateFlag(flag) || flag.length() == 1) {
            return;
        }
        try {
            extractDateTolerance(flag);
        } catch (IllegalArgumentException e) {
            String fieldName = extractFieldName(rawKey);
            if (fieldName == null || fieldName.isEmpty()) {
                fieldName = rawKey;
            }
            String location = caseLocation == null || caseLocation.trim().isEmpty()
                    ? "(unknown)" : caseLocation;
            throw new IllegalArgumentException(
                    "[JustTest] Invalid date tolerance [" + flag + "] on field '" + fieldName
                            + "' in case '" + location
                            + "'. Tolerance must fit in a Java int number of seconds.",
                    e);
        }
    }

    /**
     * {@code legalFlags} 的文案必须与这里的判断一致。
     */
    private static boolean isAllowed(String flag, FlagStage stage) {
        switch (stage) {
            case PREPARE:
                return FLAG_N.equals(flag) || FLAG_F.equals(flag);
            case EXPECT:
                return FLAG_C.equals(flag) || FLAG_CN.equals(flag) || FLAG_N.equals(flag)
                        || isAssertionMatcher(flag);
            case RESPONSE:
                return FLAG_C.equals(flag) || FLAG_N.equals(flag) || isAssertionMatcher(flag);
            case EXCEPTION:
                return FLAG_N.equals(flag) || isAssertionMatcher(flag);
            default:
                throw new IllegalStateException("[JustTest] Unknown flag stage: " + stage);
        }
    }

    private static boolean isAssertionMatcher(String flag) {
        return FLAG_R.equals(flag) || FLAG_A.equals(flag) || FLAG_J.equals(flag) || isDateFlag(flag);
    }

    private static String legalFlags(FlagStage stage) {
        switch (stage) {
            case PREPARE:
                return "N, F";
            case EXPECT:
                return "C, CN, N, R, A, D, D<digits>, J";
            case RESPONSE:
                return "C, N, R, A, D, D<digits>, J";
            case EXCEPTION:
                return "N, R, A, D, D<digits>, J";
            default:
                throw new IllegalStateException("[JustTest] Unknown flag stage: " + stage);
        }
    }

    private static String unknownFlagMessage(String rawKey, String flag, FlagStage stage, String caseLocation) {
        String fieldName = extractFieldName(rawKey);
        if (fieldName == null || fieldName.isEmpty()) {
            fieldName = rawKey;
        }
        String location = caseLocation == null || caseLocation.trim().isEmpty() ? "(unknown)" : caseLocation;
        return "[JustTest] Unknown flag [" + flag + "] on field '" + fieldName
                + "' in case '" + location
                + "'. Flags are case-sensitive. Legal flags: " + legalFlags(stage);
    }

    // ==================== 统一 Flag 断言 ====================

    /**
     * 对 matcher 类 flag（A/R/D/J）执行断言，消除 DataSetVerifier、ResultVerifier
     * 与 ExceptionVerifier 的重复分发逻辑。
     *
     * <p>处理的 flag：A（非空）、R（正则）、D/Dxx（相对当前时刻的新鲜度）、J（JSON 结构）。
     * {@code [D]} 忽略 YAML 期望值。不处理 C/CN/N 和默认精确匹配（无 flag），由调用方自行处理。
     * 未知 flag 须先由 {@link #requireKnownFlags} 拒绝，本方法仍对非 matcher flag 返回 null。</p>
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
            int tolerance;
            try {
                tolerance = extractDateTolerance(flag);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "[JustTest] Invalid date tolerance [" + flag + "] at '" + fieldPath + "'", e);
            }
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
     * 创建日期新鲜度匹配器：实际时间须落在距当前时刻 {@code toleranceSeconds} 秒内。
     * YAML 期望值不参与比较。
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
                        "[JustTest] Invalid regex in [R] flag: " + regex, e);
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
            } else if (actualValue instanceof LocalDateTime) {
                actualMillis = ((LocalDateTime) actualValue)
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli();
            } else if (actualValue instanceof Instant) {
                actualMillis = ((Instant) actualValue).toEpochMilli();
            } else if (actualValue instanceof OffsetDateTime) {
                actualMillis = ((OffsetDateTime) actualValue).toInstant().toEpochMilli();
            } else if (actualValue instanceof ZonedDateTime) {
                actualMillis = ((ZonedDateTime) actualValue).toInstant().toEpochMilli();
            } else {
                // Timestamp.valueOf 只接受 yyyy-mm-dd hh:mm:ss。ISO-8601 的 'T' 先换成空格。
                try {
                    String text = actualValue.toString().replace('T', ' ');
                    actualMillis = java.sql.Timestamp.valueOf(text).getTime();
                } catch (IllegalArgumentException e) {
                    return false;
                }
            }
            long diff = Math.abs(System.currentTimeMillis() - actualMillis);
            return diff <= (long) toleranceSeconds * 1000;
        }

        @Override
        public String describe() {
            return "[D" + toleranceSeconds + "] (within " + toleranceSeconds + "s of now)";
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
            Object expected = parseJson(expectedJson, "expected");
            Object actual = parseJson(actualValue.toString(), "actual");
            return jsonEquals(expected, actual);
        }

        @Override
        public String describe() {
            return "[J](" + expectedJson + ")";
        }

        private static Object parseJson(String text, String side) {
            try {
                return JSON.parse(text);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(
                        "[JustTest] [J] failed to parse " + side + " JSON <" + text + ">: "
                                + e.getMessage(),
                        e);
            }
        }

        /**
         * 数值先归一成 BigDecimal，再用 {@link BigDecimal#compareTo} 比较，因此 {@code 1} 与 {@code 1.0} 相等。
         * 不改动字符串、布尔或对象结构的比较。
         */
        private static boolean jsonEquals(Object left, Object right) {
            if (left == right) {
                return true;
            }
            if (left == null || right == null) {
                return false;
            }
            if (left instanceof Number && right instanceof Number) {
                return toBigDecimal((Number) left).compareTo(toBigDecimal((Number) right)) == 0;
            }
            if (left instanceof Map && right instanceof Map) {
                Map<?, ?> leftMap = (Map<?, ?>) left;
                Map<?, ?> rightMap = (Map<?, ?>) right;
                if (leftMap.size() != rightMap.size()) {
                    return false;
                }
                for (Map.Entry<?, ?> entry : leftMap.entrySet()) {
                    if (!rightMap.containsKey(entry.getKey())) {
                        return false;
                    }
                    if (!jsonEquals(entry.getValue(), rightMap.get(entry.getKey()))) {
                        return false;
                    }
                }
                return true;
            }
            if (left instanceof List && right instanceof List) {
                List<?> leftList = (List<?>) left;
                List<?> rightList = (List<?>) right;
                if (leftList.size() != rightList.size()) {
                    return false;
                }
                for (int i = 0; i < leftList.size(); i++) {
                    if (!jsonEquals(leftList.get(i), rightList.get(i))) {
                        return false;
                    }
                }
                return true;
            }
            return left.equals(right);
        }

        private static BigDecimal toBigDecimal(Number number) {
            if (number instanceof BigDecimal) {
                return (BigDecimal) number;
            }
            if (number instanceof BigInteger) {
                return new BigDecimal((BigInteger) number);
            }
            if (number instanceof Long || number instanceof Integer
                    || number instanceof Short || number instanceof Byte) {
                return BigDecimal.valueOf(number.longValue());
            }
            return new BigDecimal(number.toString());
        }
    }
}

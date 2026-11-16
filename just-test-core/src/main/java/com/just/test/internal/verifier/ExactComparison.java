package com.just.test.internal.verifier;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;

/**
 * {@code expect.yaml} 与 {@code response.yaml} 共用的精确比较细节。
 *
 * <p>不是稳定 API。只收拢两条路径上已经一致的 BigDecimal 标度比较，
 * 以及 {@link Timestamp} 尾部 {@code .0} 的字符串归一。
 * 不增加日期或数值的智能比较。</p>
 */
final class ExactComparison {

    private ExactComparison() {
    }

    /**
     * 任一侧已经是 BigDecimal 时，用 {@link BigDecimal#compareTo} 忽略标度。
     * 另一侧只在它是 Number 或数字字符串时转换。没有 BigDecimal 的数值对继续走字符串精确比较。
     */
    static boolean decimalScalesMatch(Object expectValue, Object actualValue) {
        if (!(expectValue instanceof BigDecimal) && !(actualValue instanceof BigDecimal)) {
            return false;
        }
        BigDecimal expected = toComparableDecimal(expectValue);
        BigDecimal actual = toComparableDecimal(actualValue);
        return expected != null && actual != null && expected.compareTo(actual) == 0;
    }

    /**
     * BigDecimal 用 {@link BigDecimal#toPlainString()}。
     * {@link Timestamp} 去掉尾部全零小数（{@code .0}、{@code .00}、{@code .000}），
     * 使 JDBC 的 {@code 2026-11-13 10:00:00.0} 与 YAML 的 {@code 2026-11-13 10:00:00} 相同。
     * 其它类型保持 {@link Object#toString()}。
     */
    static String normalizeActual(Object value) {
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).toPlainString();
        }
        if (value instanceof Timestamp) {
            return value.toString().replaceAll("\\.0+$", "");
        }
        return value.toString();
    }

    private static BigDecimal toComparableDecimal(Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof BigInteger) {
            return new BigDecimal((BigInteger) value);
        }
        if (value instanceof Long || value instanceof Integer
                || value instanceof Short || value instanceof Byte) {
            return BigDecimal.valueOf(((Number) value).longValue());
        }
        if (value instanceof Number) {
            try {
                return new BigDecimal(value.toString());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        if (value instanceof String) {
            try {
                return new BigDecimal((String) value);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }
}

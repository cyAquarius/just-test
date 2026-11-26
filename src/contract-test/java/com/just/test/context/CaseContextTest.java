package com.just.test.context;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseContextTest {

    @Test
    void readsBooleanAndDoubleStringsWithoutSilentlyCoercingInvalidValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("truth", "TrUe");
        values.put("falsehood", "FALSE");
        values.put("invalid", Arrays.asList("Y", "ture", 1));
        values.put("amounts", Arrays.asList("1.25", 2));
        values.put("badNumber", "garbage");
        CaseContext context = new CaseContext("conversion", "test/conversion", values);

        assertEquals(Boolean.TRUE, context.getObject("truth", Boolean.class));
        assertEquals(Boolean.FALSE, context.getObject("falsehood", Boolean.class));
        assertEquals(Arrays.asList(1.25, 2.0), context.getList("amounts", Double.class));
        assertThrows(IllegalArgumentException.class, () -> context.getList("invalid", Boolean.class));
        assertThrows(IllegalArgumentException.class, () -> context.getObject("badNumber", Double.class));
    }

    @Test
    void conversionErrorsIncludeKeyAndCase() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ok", Integer.valueOf(4));
        params.put("wide", Long.valueOf(8L));
        params.put("count", "nope");
        params.put("total", "nope");
        params.put("amount", "12.3.4");
        params.put("ids", Arrays.asList("1", "x"));
        CaseContext context = new CaseContext("overflow-case", "com/example/overflow-case", params);

        assertEquals(Integer.valueOf(4), context.getInt("ok"));
        assertEquals(Integer.valueOf(8), context.getInt("wide"));

        assertConversion(assertThrows(IllegalArgumentException.class, () -> context.getInt("count")),
                "count", "Integer");
        assertConversion(assertThrows(IllegalArgumentException.class, () -> context.getLong("total")),
                "total", "Long");
        assertConversion(assertThrows(IllegalArgumentException.class,
                () -> context.getObject("amount", BigDecimal.class)), "amount", "BigDecimal");
        assertConversion(assertThrows(IllegalArgumentException.class,
                () -> context.getList("ids", Integer.class)), "ids", "List");
    }

    @Test
    void integerConversionsRejectOverflowAndFractions() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("wide", 3000000000L);
        params.put("fraction", 1.9);
        params.put("oversized", BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE));
        params.put("values", Arrays.asList(1, new BigDecimal("2.01")));
        params.put("nearLimit", Math.nextDown(0x1.0p63));
        CaseContext context = new CaseContext("overflow-case", "com/example/overflow-case", params);

        assertConversion(assertThrows(IllegalArgumentException.class, () -> context.getInt("wide")),
                "wide", "Integer");
        assertConversion(assertThrows(IllegalArgumentException.class, () -> context.getLong("fraction")),
                "fraction", "Long");
        assertConversion(assertThrows(IllegalArgumentException.class,
                () -> context.getObject("oversized", Long.class)), "oversized", "Long");
        assertConversion(assertThrows(IllegalArgumentException.class,
                () -> context.getList("values", Integer.class)), "values", "Integer");
        assertEquals(Long.valueOf(9223372036854774784L), context.getLong("nearLimit"));
    }

    @Test
    void integerStringsConvertConsistentlyAcrossAccessors() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", "101");
        params.put("ids", Arrays.asList("101", "-102"));
        CaseContext context = new CaseContext("ids", "com/example/ids", params);

        assertEquals(Long.valueOf(101L), context.getLong("id"));
        assertEquals(Integer.valueOf(101), context.getInt("id"));
        assertEquals(context.getLong("id"), context.getObject("id", Long.class));
        assertEquals(context.getInt("id"), context.getObject("id", Integer.class));
        assertEquals(Collections.singletonList(context.getLong("id")), context.getList("id", Long.class));
        assertEquals(Arrays.asList(101L, -102L), context.getList("ids", Long.class));
        assertEquals(Arrays.asList(101, -102), context.getList("ids", Integer.class));
    }

    private static void assertConversion(IllegalArgumentException error, String key, String type) {
        String message = error.getMessage();
        assertTrue(message.contains("'" + key + "'"), message);
        assertTrue(message.contains(type), message);
        assertTrue(message.contains("overflow-case"), message);
        assertTrue(message.contains("com/example/overflow-case"), message);
        assertTrue(error.getCause() != null, message);
    }
}

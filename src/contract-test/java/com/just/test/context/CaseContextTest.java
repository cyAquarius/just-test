package com.just.test.context;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseContextTest {

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

    private static void assertConversion(IllegalArgumentException error, String key, String type) {
        String message = error.getMessage();
        assertTrue(message.contains("'" + key + "'"), message);
        assertTrue(message.contains(type), message);
        assertTrue(message.contains("overflow-case"), message);
        assertTrue(message.contains("com/example/overflow-case"), message);
        assertTrue(error.getCause() != null, message);
    }
}

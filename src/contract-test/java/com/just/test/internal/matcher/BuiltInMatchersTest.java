package com.just.test.internal.matcher;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltInMatchersTest {

    @Test
    void dateFlagIgnoresYamlExpectedValueAndComparesAgainstNow() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        String ignoredExpected = "1999-01-01 00:00:00";

        String passed = BuiltInMatchers.assertMatcherFlag(
                "D60", ignoredExpected, now, "row.gmt_create");
        assertEquals("", passed);

        Timestamp tooOld = new Timestamp(System.currentTimeMillis() - 120_000L);
        String failed = BuiltInMatchers.assertMatcherFlag(
                "D60", ignoredExpected, tooOld, "row.gmt_create");
        assertTrue(failed.contains("within 60s of now"), failed);
        assertFalse(failed.contains(ignoredExpected), failed);
    }

    @Test
    void dateMatcherDescribeStatesFreshnessAgainstNow() {
        assertEquals("[D60] (within 60s of now)", BuiltInMatchers.dateMatcher(60).describe());
    }

    @Test
    void acceptsOnlyFlagsLegalForEachStage() {
        assertStage(BuiltInMatchers.FlagStage.PREPARE, "N, F",
                new String[]{"N", "F"},
                new String[]{"n", "f", "C", "CN", "R", "A", "D", "D60", "J", "CC"});
        assertStage(BuiltInMatchers.FlagStage.EXPECT, "C, CN, N, R, A, D, D<digits>, J",
                new String[]{"C", "CN", "N", "R", "A", "D", "D0", "D60", "J"},
                new String[]{"c", "cn", "CC", "NN", "F", "f", "d60", "Y", "D60x"});
        assertStage(BuiltInMatchers.FlagStage.RESPONSE, "C, N, R, A, D, D<digits>, J",
                new String[]{"C", "N", "R", "A", "D", "D60", "J"},
                new String[]{"c", "cn", "CN", "CC", "F", "Y"});
        assertStage(BuiltInMatchers.FlagStage.EXCEPTION, "N, R, A, D, D<digits>, J",
                new String[]{"N", "R", "A", "D", "D60", "J"},
                new String[]{"n", "cn", "C", "CN", "CC", "F", "Y"});
    }

    @Test
    void reportsNestedFieldAndFlagOnlyKey() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id[cn]", 1);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("sample", Arrays.asList(row));

        IllegalArgumentException nested = assertThrows(IllegalArgumentException.class, () ->
                BuiltInMatchers.requireKnownFlags(root, BuiltInMatchers.FlagStage.EXPECT,
                        "com/example/case/expect.yaml"));
        assertTrue(nested.getMessage().contains("field 'id'"), nested.getMessage());
        assertTrue(nested.getMessage().contains("com/example/case/expect.yaml"), nested.getMessage());

        Map<String, Object> flagOnly = new LinkedHashMap<>();
        flagOnly.put("[CC]", "nope");
        IllegalArgumentException bare = assertThrows(IllegalArgumentException.class, () ->
                BuiltInMatchers.requireKnownFlags(flagOnly, BuiltInMatchers.FlagStage.RESPONSE, "  "));
        assertTrue(bare.getMessage().contains("field '[CC]'"), bare.getMessage());
        assertTrue(bare.getMessage().contains("'(unknown)'"), bare.getMessage());
        assertTrue(bare.getMessage().contains("case-sensitive"), bare.getMessage());
    }

    private static void assertStage(BuiltInMatchers.FlagStage stage, String legalFlags,
                                    String[] accepted, String[] rejected) {
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("plain", 1);
        for (String flag : accepted) {
            ok.put("col[" + flag + "]", 1);
        }
        BuiltInMatchers.requireKnownFlags(ok, stage, "case/file.yaml");

        for (String flag : rejected) {
            Map<String, Object> bad = new LinkedHashMap<>();
            bad.put("col[" + flag + "]", 1);
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                    BuiltInMatchers.requireKnownFlags(bad, stage, "case/file.yaml"));
            String message = error.getMessage();
            assertTrue(message.contains("field 'col'"), message);
            assertTrue(message.contains("[" + flag + "]"), message);
            assertTrue(message.contains("case/file.yaml"), message);
            assertTrue(message.contains("case-sensitive"), message);
            assertTrue(message.contains("Legal flags: " + legalFlags), message);
        }
    }
}

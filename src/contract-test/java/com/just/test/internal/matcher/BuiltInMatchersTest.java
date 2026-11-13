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
    void jsonMatcherComparesNumbersAsBigDecimal() {
        assertEquals("", BuiltInMatchers.assertMatcherFlag("J", "1", "1.0", "row.n"));
        assertEquals("", BuiltInMatchers.assertMatcherFlag(
                "J", "{\"n\":1,\"items\":[1.0,2]}", "{\"items\":[1,2.00],\"n\":1.0}", "row.ext"));

        String mismatch = BuiltInMatchers.assertMatcherFlag("J", "1", "2", "row.n");
        assertTrue(mismatch.contains("row.n"), mismatch);
        assertFalse(BuiltInMatchers.jsonMatcher("\"1\"").matches("1"));
    }

    @Test
    void jsonMatcherReportsParseFailureInsteadOfStringFallback() {
        IllegalArgumentException sameText = assertThrows(IllegalArgumentException.class,
                () -> BuiltInMatchers.assertMatcherFlag("J", "not-json", "not-json", "row.ext"));
        assertTrue(sameText.getMessage().contains("[J]"), sameText.getMessage());
        assertTrue(sameText.getMessage().contains("not-json"), sameText.getMessage());
        assertTrue(sameText.getMessage().contains("failed to parse"), sameText.getMessage());

        IllegalArgumentException braces = assertThrows(IllegalArgumentException.class,
                () -> BuiltInMatchers.jsonMatcher("{").matches("{"));
        assertTrue(braces.getMessage().contains("failed to parse"), braces.getMessage());
    }

    @Test
    void dateToleranceOverflowNamesFieldAndCase() {
        assertInvalidDateTolerance("D99999999999");
    }

    @Test
    void unicodeDateDigitsKeepIntegerParseSemantics() {
        assertEquals(123, BuiltInMatchers.extractDateTolerance("D\uFF11\uFF12\uFF13"));
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

    private static void assertInvalidDateTolerance(String flag) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("gmt[" + flag + "]", "ignored");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                BuiltInMatchers.requireKnownFlags(node, BuiltInMatchers.FlagStage.EXPECT,
                        "com/example/date-case/expect.yaml"));
        String message = error.getMessage();
        assertTrue(message.contains("field 'gmt'"), message);
        assertTrue(message.contains("[" + flag + "]"), message);
        assertTrue(message.contains("com/example/date-case/expect.yaml"), message);
        assertTrue(message.contains("Java int"), message);

        IllegalArgumentException direct = assertThrows(IllegalArgumentException.class, () ->
                BuiltInMatchers.assertMatcherFlag(flag, "ignored", new Timestamp(System.currentTimeMillis()),
                        "row.gmt"));
        assertTrue(direct.getMessage().contains("row.gmt"), direct.getMessage());
        assertTrue(direct.getMessage().contains("[" + flag + "]"), direct.getMessage());
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

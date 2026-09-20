package com.just.test.internal.matcher;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}

package com.just.test.smarttest.internal.verifier;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultVerifierTest {

    private static final String CASE_PATH = "com/just/test/smarttest/verifier/result/flags";

    @Test
    void verifiesNestedFlagsAndUnorderedList() {
        Map<String, Object> actual = new LinkedHashMap<>();
        actual.put("status", "ready");
        actual.put("ignored", "actual-value");
        actual.put("metadata", "{\"items\":[2,1],\"enabled\":true}");
        actual.put("items", Arrays.asList(item(1, "first"), item(2, "second")));

        List<String> failures = ResultVerifier.verify(actual, CASE_PATH);

        assertTrue(failures.isEmpty(), failures.toString());
    }

    @Test
    void reportsExactValueMismatch() {
        Map<String, Object> actual = new LinkedHashMap<>();
        actual.put("status", "wrong");
        actual.put("ignored", "actual-value");
        actual.put("metadata", "{\"enabled\":true,\"items\":[2,1]}");
        actual.put("items", Arrays.asList(item(2, "second"), item(1, "first")));

        List<String> failures = ResultVerifier.verify(actual, CASE_PATH);

        assertTrue(failures.stream().anyMatch(failure -> failure.contains("result.status")));
    }

    @Test
    void rejectsMixedKeyedAndUnkeyedUnorderedItems() {
        List<Map<String, Object>> actual = Arrays.asList(item(2, "second"), item(1, "first"));

        List<String> failures = ResultVerifier.verify(actual,
                "com/just/test/smarttest/verifier/result/mixed-keys");

        assertTrue(failures.stream().anyMatch(
                failure -> failure.contains("every expected item must declare at least one [C] field")));
    }

    @Test
    void rejectsMixedKeyedAndUnkeyedItemsInNestedList() {
        Map<String, Object> actual = new LinkedHashMap<>();
        actual.put("items", Arrays.asList(item(1, "first"), item(2, "second")));

        List<String> failures = ResultVerifier.verify(actual,
                "com/just/test/smarttest/verifier/result/nested-mixed-keys");

        assertTrue(failures.stream().anyMatch(
                failure -> failure.contains("every expected item must declare at least one [C] field")));
    }

    private static Map<String, Object> item(int id, String name) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("name", name);
        return item;
    }
}

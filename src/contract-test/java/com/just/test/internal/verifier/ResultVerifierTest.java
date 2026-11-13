package com.just.test.internal.verifier;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultVerifierTest {

    private static final String CASE_PATH = "com/just/test/verifier/result/flags";
    private static final String RESPONSE_FLAGS = "C, N, R, A, D, D<digits>, J";

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
                "com/just/test/verifier/result/mixed-keys");

        assertTrue(failures.stream().anyMatch(
                failure -> failure.contains("every expected item must declare at least one [C] field")));
    }

    @Test
    void rejectsMixedKeyedAndUnkeyedItemsInNestedList() {
        Map<String, Object> actual = new LinkedHashMap<>();
        actual.put("items", Arrays.asList(item(1, "first"), item(2, "second")));

        List<String> failures = ResultVerifier.verify(actual,
                "com/just/test/verifier/result/nested-mixed-keys");

        assertTrue(failures.stream().anyMatch(
                failure -> failure.contains("every expected item must declare at least one [C] field")));
    }

    @Test
    void rejectsLowercaseCnInsteadOfExactMatch() {
        Map<String, Object> actual = new LinkedHashMap<>();
        actual.put("status", "ready");

        String casePath = "com/just/test/verifier/result/flag-cn";
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ResultVerifier.verify(actual, casePath));
        assertUnknownFlag(error, "status", "cn", casePath + "/response.yaml", RESPONSE_FLAGS);
    }

    @Test
    void rejectsMisspelledAndWrongStageResponseFlags() {
        assertUnknownResponse("com/just/test/verifier/result/flag-cc", "name", "CC");
        assertUnknownResponse("com/just/test/verifier/result/flag-stage-cn", "id", "CN");
        assertUnknownResponse("com/just/test/verifier/result/flag-cc-key", "[CC]", "CC");
    }

    private static void assertUnknownResponse(String casePath, String field, String flag) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ResultVerifier.verify(new LinkedHashMap<String, Object>(), casePath));
        assertUnknownFlag(error, field, flag, casePath + "/response.yaml", RESPONSE_FLAGS);
    }

    private static void assertUnknownFlag(IllegalArgumentException error, String field, String flag,
                                          String casePath, String legalFlags) {
        String message = error.getMessage();
        assertTrue(message.contains("field '" + field + "'"), message);
        assertTrue(message.contains("[" + flag + "]"), message);
        assertTrue(message.contains(casePath), message);
        assertTrue(message.contains("case-sensitive"), message);
        assertTrue(message.contains("Legal flags: " + legalFlags), message);
    }

    private static Map<String, Object> item(int id, String name) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("name", name);
        return item;
    }
}

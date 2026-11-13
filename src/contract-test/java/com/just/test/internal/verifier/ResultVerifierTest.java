package com.just.test.internal.verifier;

import com.just.test.internal.loader.DataSetLoader;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void flagOnlyNDoesNotSkipLaterFields() {
        Map<String, Object> actual = new LinkedHashMap<>();
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("status", "wrong");
        actual.put("nested", nested);

        for (String casePath : Arrays.asList(
                "com/just/test/verifier/result/flag-n-first",
                "com/just/test/verifier/result/flag-n-last")) {
            List<String> failures = ResultVerifier.verify(actual, casePath);
            assertTrue(failures.stream().anyMatch(failure -> failure.contains("result.nested.status")),
                    casePath + " " + failures);
        }
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

    @Test
    void matchesYamlIntegerAndNumericStringToBigDecimalScale() {
        Map<String, Object> actual = new LinkedHashMap<>();
        actual.put("amount", new BigDecimal("100.00"));
        actual.put("quoted", new BigDecimal("100.00"));

        List<String> failures = ResultVerifier.verify(actual,
                "com/just/test/verifier/result/decimal-scale");

        assertTrue(failures.isEmpty(), failures.toString());
    }

    @Test
    void rejectsBigDecimalThatDiffersAfterScaleNormalization() {
        Map<String, Object> actual = new LinkedHashMap<>();
        actual.put("amount", new BigDecimal("100.01"));
        actual.put("quoted", new BigDecimal("100.00"));

        List<String> failures = ResultVerifier.verify(actual,
                "com/just/test/verifier/result/decimal-scale");

        assertEquals(1, failures.size(), failures.toString());
        String failure = failures.get(0);
        assertTrue(failure.contains("result.amount"), failure);
        assertTrue(failure.contains("<100>"), failure);
        assertTrue(failure.contains("<100.01>"), failure);
    }

    @Test
    void keepsIntegerAndDoubleOnExactStrings() {
        Map<String, Object> actual = new LinkedHashMap<>();
        actual.put("count", Double.valueOf("100.0"));

        List<String> failures = ResultVerifier.verify(actual,
                "com/just/test/verifier/result/integer-double");

        assertTrue(failures.stream().anyMatch(failure ->
                failure.contains("result.count")
                        && failure.contains("<100>")
                        && failure.contains("<100.0>")), failures.toString());
    }

    @Test
    void matchesBareYamlTimestampAsTheSameString() {
        Object parsed = DataSetLoader.parseYamlRaw(
                "com/just/test/verifier/result/timestamp-string", "response.yaml");
        assertTrue(parsed instanceof Map, String.valueOf(parsed));
        Object createdAt = ((Map<?, ?>) parsed).get("createdAt");
        assertEquals("2026-11-13 10:00:00", createdAt);
        assertEquals(String.class, createdAt.getClass());

        Map<String, Object> actual = new LinkedHashMap<>();
        actual.put("createdAt", "2026-11-13 10:00:00");
        List<String> failures = ResultVerifier.verify(actual,
                "com/just/test/verifier/result/timestamp-string");

        assertTrue(failures.isEmpty(), failures.toString());
    }

    @Test
    void comparesGetterlessPojoThroughMapAndList() {
        GetterlessRow row = new GetterlessRow("alpha", 3);

        List<String> asMap = ResultVerifier.verify(row,
                "com/just/test/verifier/result/getterless-map");
        assertTrue(asMap.isEmpty(), asMap.toString());

        // Arrays.asList 已是 List，元素走 toMap。数组不是 List，整段走 toList。
        String listCase = "com/just/test/verifier/result/getterless-list";
        List<String> asList = ResultVerifier.verify(Arrays.asList(row), listCase);
        List<String> asArray = ResultVerifier.verify(new GetterlessRow[] {row}, listCase);

        assertTrue(asList.isEmpty(), asList.toString());
        assertTrue(asArray.isEmpty(), asArray.toString());
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

    private static final class GetterlessRow {
        private final String name;
        private final int qty;

        private GetterlessRow(String name, int qty) {
            this.name = name;
            this.qty = qty;
        }
    }
}

package com.just.test.internal.verifier;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExceptionVerifierTest {

    private static final String CASE_PATH = "com/just/test/verifier/exception/expected";
    private static final String EXCEPTION_FLAGS = "N, R, A, D, D<digits>, J";

    @Test
    void matchesExpectedCauseTypeAndRegexMessage() {
        Throwable actual = new RuntimeException("wrapper",
                new IllegalArgumentException("bad request 42"));

        List<String> failures = ExceptionVerifier.verify(actual, CASE_PATH);

        assertTrue(failures.isEmpty(), failures.toString());
    }

    @Test
    void reportsMissingExpectedException() {
        List<String> failures = ExceptionVerifier.verify(null, CASE_PATH);

        assertTrue(failures.stream().anyMatch(failure -> failure.contains("none was thrown")));
    }

    @Test
    void loadsYmlExpectException() {
        String casePath = "com/just/test/verifier/exception/ymlonly";

        assertTrue(ExceptionVerifier.hasExpectException(casePath));
        List<String> matched = ExceptionVerifier.verify(
                new IllegalArgumentException("from-yml"), casePath);
        assertTrue(matched.isEmpty(), matched.toString());

        List<String> missing = ExceptionVerifier.verify(null, casePath);
        assertTrue(missing.stream().anyMatch(failure -> failure.contains("none was thrown")));
    }

    @Test
    void rejectsLowercaseCnInsteadOfExactMessageMatch() {
        String casePath = "com/just/test/verifier/exception/flag-cn";
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ExceptionVerifier.verify(new IllegalArgumentException("bad request 42"), casePath));
        assertUnknownFlag(error, "message", "cn", casePath + "/expect_exception.yaml", EXCEPTION_FLAGS);
    }

    @Test
    void rejectsMisspelledExceptionFlag() {
        String casePath = "com/just/test/verifier/exception/flag-cc";
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ExceptionVerifier.verify(new IllegalArgumentException("bad request 42"), casePath));
        assertUnknownFlag(error, "message", "CC", casePath + "/expect_exception.yaml", EXCEPTION_FLAGS);
    }

    @Test
    void failsFastWhenExpectExceptionHasBothSuffixes() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ExceptionVerifier.hasExpectException(
                        "com/just/test/verifier/exception/bothsuffix"));
        assertTrue(error.getMessage().contains("same case stage"), error.getMessage());
        assertTrue(error.getMessage().contains("expect_exception.yaml"), error.getMessage());
        assertTrue(error.getMessage().contains("expect_exception.yml"), error.getMessage());
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
}

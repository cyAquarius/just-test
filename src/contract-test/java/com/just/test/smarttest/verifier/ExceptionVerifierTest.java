package com.just.test.smarttest.verifier;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ExceptionVerifierTest {

    private static final String CASE_PATH = "com/just/test/smarttest/verifier/exception/expected";

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
}

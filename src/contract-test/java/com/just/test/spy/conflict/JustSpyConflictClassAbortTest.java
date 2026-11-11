package com.just.test.spy.conflict;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;
import org.junit.platform.testkit.engine.Events;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JustSpyConflictClassAbortTest {

    @BeforeEach
    void resetRefreshAttempts() {
        SpyMockConflictCases.REFRESH_ATTEMPTS.set(0);
    }

    @Test
    void abortsTestClassOnceWhenJustMockAndJustSpyShareAType() {
        EngineExecutionResults results = EngineTestKit.engine("junit-jupiter")
                .selectors(DiscoverySelectors.selectClass(SpyMockConflictCases.class))
                .execute();

        assertEquals(1, SpyMockConflictCases.REFRESH_ATTEMPTS.get(),
                "ApplicationContext must fail once at class load, not once per YAML case");
        assertEquals(0, results.testEvents().failed().count(),
                "YAML @CaseSource invocations must not each fail with the same context error");
        String diagnostic = firstConflictMessage(results.containerEvents());
        assertTrue(diagnostic != null, "class-level failure must include the spy vs mock diagnostic");
        assertTrue(diagnostic.contains("Conflicting beans of type"));
        assertTrue(diagnostic.contains("@JustSpy"));
        assertTrue(diagnostic.contains("@JustMock"));
        assertTrue(diagnostic.contains("SharedClient"));
        assertTrue(diagnostic.contains("Candidates:"));
    }

    private static String firstConflictMessage(Events events) {
        for (Event event : events.failed().list()) {
            Optional<TestExecutionResult> result = event.getPayload(TestExecutionResult.class);
            if (!result.isPresent() || !result.get().getThrowable().isPresent()) {
                continue;
            }
            String message = flatten(result.get().getThrowable().get());
            if (message.contains("Conflicting beans of type")) {
                return message;
            }
        }
        return null;
    }

    private static String flatten(Throwable throwable) {
        StringBuilder text = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                text.append(current.getMessage()).append('\n');
            }
            current = current.getCause();
        }
        return text.toString();
    }
}

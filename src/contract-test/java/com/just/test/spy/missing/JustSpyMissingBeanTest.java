package com.just.test.spy.missing;

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

class JustSpyMissingBeanTest {

    @BeforeEach
    void resetRefreshAttempts() {
        JustSpyMissingBeanCases.REFRESH_ATTEMPTS.set(0);
    }

    @Test
    void failsFastOnceWhenJustSpyBeanDoesNotExist() {
        EngineExecutionResults results = EngineTestKit.engine("junit-jupiter")
                .selectors(DiscoverySelectors.selectClass(JustSpyMissingBeanCases.class))
                .execute();

        assertEquals(1, JustSpyMissingBeanCases.REFRESH_ATTEMPTS.get(),
                "ApplicationContext must fail once at class load, not once per YAML case");
        assertEquals(0, results.testEvents().failed().count());
        String diagnostic = firstMessage(results.containerEvents());
        assertTrue(diagnostic != null, "class-level failure must explain the missing @JustSpy bean");
        assertTrue(diagnostic.contains("No existing bean"));
        assertTrue(diagnostic.contains("MissingClient"));
        assertTrue(diagnostic.contains("does not create one"));
    }

    private static String firstMessage(Events events) {
        for (Event event : events.failed().list()) {
            Optional<TestExecutionResult> result = event.getPayload(TestExecutionResult.class);
            if (!result.isPresent() || !result.get().getThrowable().isPresent()) {
                continue;
            }
            String message = flatten(result.get().getThrowable().get());
            if (message.contains("No existing bean")) {
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

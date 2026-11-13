package com.just.test.lifecycle.beforecase;

import com.just.test.lifecycle.beforecase.parametercase.ParameterizedBeforeCaseCases;
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

class ParameterizedBeforeCaseValidationTest {

    @Test
    void parameterizedBeforeCaseFailsAtBeforeAll() {
        EngineExecutionResults results = EngineTestKit.engine("junit-jupiter")
                .selectors(DiscoverySelectors.selectClass(ParameterizedBeforeCaseCases.class))
                .execute();

        String diagnostic = containerFailure(results);
        assertTrue(diagnostic.contains(ParameterizedBeforeCaseCases.class.getName()), diagnostic);
        assertTrue(diagnostic.contains("ParameterizedBeforeCaseParent.setupWithParameter"), diagnostic);
        assertTrue(diagnostic.contains("must have no parameters"), diagnostic);
        assertEquals(0, results.testEvents().started().count(), diagnostic);
    }

    private static String containerFailure(EngineExecutionResults results) {
        String message = firstMessage(results.containerEvents().failed());
        assertTrue(message.contains("must have no parameters"),
                "BeforeAll must fail the test class. Events:\n" + flattenFailures(results.allEvents()));
        return message;
    }

    private static String firstMessage(Events events) {
        for (Event event : events.list()) {
            Optional<TestExecutionResult> result = event.getPayload(TestExecutionResult.class);
            if (!result.isPresent() || !result.get().getThrowable().isPresent()) {
                continue;
            }
            return flatten(result.get().getThrowable().get());
        }
        return "";
    }

    private static String flattenFailures(Events events) {
        StringBuilder text = new StringBuilder();
        for (Event event : events.failed().list()) {
            Optional<TestExecutionResult> result = event.getPayload(TestExecutionResult.class);
            if (!result.isPresent() || !result.get().getThrowable().isPresent()) {
                continue;
            }
            text.append(flatten(result.get().getThrowable().get())).append('\n');
        }
        return text.toString();
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

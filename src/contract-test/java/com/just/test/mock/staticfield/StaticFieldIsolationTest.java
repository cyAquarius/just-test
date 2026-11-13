package com.just.test.mock.staticfield;

import com.just.test.mock.staticfield.instancecase.InstanceMockSpyFieldCases;
import com.just.test.mock.staticfield.mockcase.StaticJustMockFieldCases;
import com.just.test.mock.staticfield.spycase.StaticJustSpyFieldCases;
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

class StaticFieldIsolationTest {

    @BeforeEach
    void reset() {
        StaticJustMockFieldCases.INVOCATIONS.set(0);
        StaticJustMockFieldCases.REFRESH_ATTEMPTS.set(0);
        StaticJustSpyFieldCases.INVOCATIONS.set(0);
        StaticJustSpyFieldCases.REFRESH_ATTEMPTS.set(0);
        InstanceMockSpyFieldCases.SEEN_GATEWAY.set(null);
        InstanceMockSpyFieldCases.SEEN_CLOCK.set(null);
    }

    @Test
    void staticJustMockFieldFailsAtBeforeAll() {
        EngineExecutionResults results = execute(StaticJustMockFieldCases.class);

        String diagnostic = containerFailure(results);
        assertTrue(diagnostic.contains("sharedGateway"), diagnostic);
        assertTrue(diagnostic.contains("breaks thread isolation"), diagnostic);
        assertTrue(diagnostic.contains("@JustMock"), diagnostic);
        assertEquals(0, StaticJustMockFieldCases.INVOCATIONS.get(), diagnostic);
        assertEquals(0, StaticJustMockFieldCases.REFRESH_ATTEMPTS.get(), diagnostic);
        assertEquals(0, results.testEvents().started().count(), diagnostic);
    }

    @Test
    void staticJustSpyFieldFailsAtBeforeAll() {
        EngineExecutionResults results = execute(StaticJustSpyFieldCases.class);

        String diagnostic = containerFailure(results);
        assertTrue(diagnostic.contains("sharedClock"), diagnostic);
        assertTrue(diagnostic.contains("breaks thread isolation"), diagnostic);
        assertTrue(diagnostic.contains("@JustSpy"), diagnostic);
        assertEquals(0, StaticJustSpyFieldCases.INVOCATIONS.get(), diagnostic);
        assertEquals(0, StaticJustSpyFieldCases.REFRESH_ATTEMPTS.get(), diagnostic);
        assertEquals(0, results.testEvents().started().count(), diagnostic);
    }

    @Test
    void instanceMockAndSpyFieldsStillInject() {
        EngineExecutionResults results = execute(InstanceMockSpyFieldCases.class);

        assertEquals(0, results.testEvents().failed().count(), flattenFailures(results.allEvents()));
        assertEquals(1, results.testEvents().succeeded().count());
        assertEquals("stubbed", InstanceMockSpyFieldCases.SEEN_GATEWAY.get());
        assertEquals("frozen", InstanceMockSpyFieldCases.SEEN_CLOCK.get());
    }

    private static EngineExecutionResults execute(Class<?> testClass) {
        return EngineTestKit.engine("junit-jupiter")
                .selectors(DiscoverySelectors.selectClass(testClass))
                .execute();
    }

    private static String containerFailure(EngineExecutionResults results) {
        String message = firstMessage(results.containerEvents().failed());
        assertTrue(message.contains("breaks thread isolation"),
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

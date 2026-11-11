package com.just.test.mock.interceptmerge;

import com.just.test.mock.interceptmerge.east.EastLaneCases;
import com.just.test.mock.interceptmerge.fixtures.EastLane;
import com.just.test.mock.interceptmerge.fixtures.Lane;
import com.just.test.mock.interceptmerge.lane.LaneCases;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;
import org.junit.platform.testkit.engine.Events;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InterceptMergeFailFastTest {

    @Test
    void ambiguousSecondDeclarationFailsAtContextStart() {
        Events events = EngineTestKit.engine("junit-jupiter")
                .selectors(DiscoverySelectors.selectClass(EastLaneCases.class))
                .execute()
                .allEvents();
        String diagnostic = firstMessage(events, "Cannot merge");
        assertTrue(diagnostic.contains("Cannot merge"), diagnostic);
        assertTrue(diagnostic.contains("Multiple beans"), diagnostic);
        assertTrue(diagnostic.contains(Lane.class.getName()), diagnostic);
        assertTrue(diagnostic.contains(EastLane.class.getName()), diagnostic);
        assertTrue(diagnostic.contains("'eastLane'"), diagnostic);
        assertTrue(diagnostic.contains(LaneCases.class.getName()), diagnostic);
        assertTrue(diagnostic.contains(EastLaneCases.class.getName()), diagnostic);
    }

    private static String firstMessage(Events events, String marker) {
        for (Event event : events.failed().list()) {
            Optional<TestExecutionResult> result = event.getPayload(TestExecutionResult.class);
            if (!result.isPresent() || !result.get().getThrowable().isPresent()) {
                continue;
            }
            String message = flatten(result.get().getThrowable().get());
            if (message.contains(marker)) {
                return message;
            }
        }
        return "";
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

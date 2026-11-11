package com.just.test.mock.intercept;

import com.just.test.mock.interceptfinal.FinalClockCases;
import com.just.test.mock.interceptoutside.OutsideMissCases;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;
import org.junit.platform.testkit.engine.Events;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InterceptFailFastTest {

    @Test
    void missingInterceptFailsFastInsteadOfReplacingTheBean() {
        Events events = EngineTestKit.engine("junit-jupiter")
                .selectors(DiscoverySelectors.selectClass(OutsideMissCases.class))
                .execute()
                .allEvents();
        String diagnostic = firstMessage(events, "was not installed");
        assertTrue(diagnostic.contains("was not installed"), diagnostic);
        assertTrue(diagnostic.contains("OrphanClock"), diagnostic);
        assertTrue(diagnostic.contains("interceptMocks"), diagnostic);
        assertTrue(diagnostic.contains("startup-class scan"), diagnostic);
    }

    @Test
    void finalTypeFailsFastAtContextStart() {
        Events events = EngineTestKit.engine("junit-jupiter")
                .selectors(DiscoverySelectors.selectClass(FinalClockCases.class))
                .execute()
                .allEvents();
        String diagnostic = firstMessage(events, "Cannot intercept");
        assertTrue(diagnostic.contains("FinalClock"), diagnostic);
        assertTrue(diagnostic.contains("mock-maker") || diagnostic.contains("final"), diagnostic);
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

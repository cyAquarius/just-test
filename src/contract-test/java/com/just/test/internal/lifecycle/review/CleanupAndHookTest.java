package com.just.test.internal.lifecycle.review;

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

class CleanupAndHookTest {

    @Test
    void hooksKeepBodyFailureAndLeakingAbortFailsWithoutCreatingLazyBeans() {
        EngineExecutionResults results = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(CleanupAndHookCases.class))
                .configurationParameter("junit.jupiter.execution.parallel.enabled", "false")
                .execute();
        results.containerEvents().assertStatistics(stats -> stats.failed(0));
        results.testEvents().assertStatistics(stats -> stats.started(6).failed(4).aborted(1).succeeded(1));
        for (Event event : results.testEvents().failed().list()) {
            String name = event.getTestDescriptor().getDisplayName();
            Throwable failure = event.getPayload(TestExecutionResult.class).get().getThrowable().get();
            if (name.startsWith("verify-")) {
                assertTrue(failure.getMessage().contains(name));
                assertTrue(Arrays.stream(failure.getSuppressed())
                        .anyMatch(original -> "body-failure".equals(original.getMessage())));
            } else {
                assertTrue(failure.getMessage().contains("Unfinished Spring transaction leaked"));
            }
        }
    }
}

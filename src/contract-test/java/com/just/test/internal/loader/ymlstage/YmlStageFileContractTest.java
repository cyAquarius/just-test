package com.just.test.internal.loader.ymlstage;

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YmlStageFileContractTest {

    @Test
    void ymlStagesApplyAndSameStageDualSuffixFailsFast() {
        EngineExecutionResults results = EngineTestKit.engine("junit-jupiter")
                .selectors(DiscoverySelectors.selectClass(YmlStageFileCases.class))
                .execute();

        Set<String> succeeded = displayNames(results.testEvents().succeeded().list());
        Map<String, String> failed = failureMessages(results.testEvents().failed().list());

        assertEquals(setOf("yaml-applied", "yml-applied"), succeeded, succeeded.toString());
        assertEquals(setOf("dual-prepare", "yml-expect-miss", "yml-response-miss"),
                failed.keySet(), failed.toString());

        String expectMiss = failed.get("yml-expect-miss");
        assertTrue(expectMiss.contains("parallel_case_record"), expectMiss);
        assertTrue(expectMiss.contains("expected <other>"), expectMiss);

        String responseMiss = failed.get("yml-response-miss");
        assertTrue(responseMiss.contains("expected <other>"), responseMiss);
        assertTrue(responseMiss.contains("prepared"), responseMiss);

        String dual = failed.get("dual-prepare");
        assertTrue(dual.contains("same case stage"), dual);
        assertTrue(dual.contains("dual-prepare/prepare.yaml"), dual);
        assertTrue(dual.contains("dual-prepare/prepare.yml"), dual);
    }

    private static Set<String> displayNames(List<Event> events) {
        Set<String> names = new LinkedHashSet<String>();
        for (Event event : events) {
            names.add(event.getTestDescriptor().getDisplayName());
        }
        return names;
    }

    private static Map<String, String> failureMessages(List<Event> events) {
        Map<String, String> messages = new LinkedHashMap<String, String>();
        for (Event event : events) {
            Optional<TestExecutionResult> result = event.getPayload(TestExecutionResult.class);
            String message = "";
            if (result.isPresent() && result.get().getThrowable().isPresent()) {
                message = flatten(result.get().getThrowable().get());
            }
            messages.put(event.getTestDescriptor().getDisplayName(), message);
        }
        return messages;
    }

    private static String flatten(Throwable throwable) {
        StringBuilder text = new StringBuilder();
        append(text, throwable);
        return text.toString();
    }

    private static void append(StringBuilder text, Throwable throwable) {
        if (throwable == null) {
            return;
        }
        if (throwable.getMessage() != null) {
            text.append(throwable.getMessage()).append('\n');
        }
        for (Throwable suppressed : throwable.getSuppressed()) {
            append(text, suppressed);
        }
        append(text, throwable.getCause());
    }

    private static Set<String> setOf(String first, String second) {
        Set<String> names = new LinkedHashSet<String>();
        names.add(first);
        names.add(second);
        return names;
    }

    private static Set<String> setOf(String first, String second, String third) {
        Set<String> names = setOf(first, second);
        names.add(third);
        return names;
    }
}

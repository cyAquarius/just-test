package com.just.test.internal.loader.casestages;

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;
import org.junit.platform.testkit.engine.Events;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseStageContractTest {

    @Test
    void runsEachCaseWithItsOwnStagesAndOutcome() {
        EngineExecutionResults results = EngineTestKit.engine("junit-jupiter")
                .selectors(DiscoverySelectors.selectClass(CaseStageCases.class))
                .execute();

        Events tests = results.testEvents();
        assertEquals(4, tests.succeeded().count());
        for (String name : new String[]{"case with spaces +", "中文用例", "empty-lists", "empty-request"}) {
            assertEquals(1, named(tests.succeeded(), name).count(), name);
        }
        assertEquals(2, tests.aborted().count());
        assertMessage(failure(tests.aborted(), "body-abort"), "aborted in test body");
        assertMessage(failure(tests.aborted(), "before-abort"), "aborted in before case");

        Events failed = tests.failed();
        assertEquals(9, failed.count());
        Throwable requestFailure = failure(failed, "broken-request");
        assertMessage(requestFailure, "Failed to parse YAML", casePath("broken-request") + "/request.yaml");
        assertNotNull(requestFailure.getCause());
        assertMessage(failure(failed, "no-stages"), "No loadable stage files", casePath("no-stages"));

        Throwable assertion = failure(failed, "before-assertion");
        assertEquals(AssertionError.class, assertion.getClass());
        assertMessage(assertion, "assertion in before case");

        for (String stage : new String[]{"prepare", "expect"}) {
            assertTableFailure(failed, stage, "mapping", "Expected a list", "LinkedHashMap");
            assertTableFailure(failed, stage, "scalar", "Expected a list", "Integer");
            assertTableFailure(failed, stage, "row", "row[1]", "Integer");
        }
    }

    private static void assertTableFailure(Events events, String stage, String shape,
                                           String problem, String type) {
        String name = stage + "-" + shape;
        assertMessage(failure(events, name), casePath(name) + "/" + stage + ".yaml",
                "parallel_case_record", problem, type);
    }

    private static String casePath(String name) {
        return CaseStageCases.class.getPackage().getName().replace('.', '/') + "/" + name;
    }

    private static Throwable failure(Events events, String name) {
        List<Throwable> failures = named(events, name)
                .map(event -> event.getRequiredPayload(TestExecutionResult.class).getThrowable().get())
                .collect(Collectors.toList());
        assertEquals(1, failures.size(), name + ": " + events.list());
        return failures.get(0);
    }

    private static Stream<Event> named(Events events, String name) {
        return events.stream().filter(event -> name.equals(event.getTestDescriptor().getDisplayName()));
    }

    private static void assertMessage(Throwable failure, String... fragments) {
        String message = failure.getMessage();
        for (String fragment : fragments) {
            assertTrue(message.contains(fragment), message);
        }
    }
}

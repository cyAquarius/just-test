package com.just.test.transactionleak;

import com.just.test.transactionleak.control.TransactionRollbackControlCases;
import com.just.test.transactionleak.leaking.TransactionLeakCases;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionLeakIsolationTest {

    @Test
    void expectedBusinessExceptionStillFailsItsSourceCaseAndNextCaseIsIsolated() {
        EngineExecutionResults results = execute(TransactionLeakCases.class);

        assertEquals(1, results.testEvents().failed().count());
        assertEquals(1, results.testEvents().succeeded().count(),
                "the normal case after the leak must run successfully on the same thread");
        Throwable failure = firstFailure(results);
        assertNotNull(failure);
        String diagnostic = flatten(failure);
        assertTrue(diagnostic.contains("Unfinished Spring transaction leaked at case boundary"));
        assertTrue(diagnostic.contains("case=a-leak"));
        assertTrue(diagnostic.contains("thread="));
        assertTrue(diagnostic.contains("actualTransactionActive=true"));
        assertTrue(diagnostic.contains("synchronizationActive=true"));
        assertTrue(diagnostic.contains("synchronizationCount="));
        assertTrue(diagnostic.contains("resourceKeysBeforeCleanup="));
        assertTrue(diagnostic.contains("remainingResourceKeys="));
        assertTrue(diagnostic.contains("commit or roll back transactions on every path"));
        assertFalse(diagnostic.contains("No value for key"));
    }

    @Test
    void explicitRollbackKeepsBothCasesHealthy() {
        EngineExecutionResults results = execute(TransactionRollbackControlCases.class);

        assertEquals(0, results.testEvents().failed().count());
        assertEquals(2, results.testEvents().succeeded().count());
    }

    private EngineExecutionResults execute(Class<?> testClass) {
        return EngineTestKit.engine("junit-jupiter")
                .selectors(DiscoverySelectors.selectClass(testClass))
                .execute();
    }

    private Throwable firstFailure(EngineExecutionResults results) {
        for (Event event : results.testEvents().failed().list()) {
            Optional<TestExecutionResult> result = event.getPayload(TestExecutionResult.class);
            if (result.isPresent() && result.get().getThrowable().isPresent()) {
                return result.get().getThrowable().get();
            }
        }
        return null;
    }

    private String flatten(Throwable throwable) {
        StringBuilder text = new StringBuilder();
        append(text, throwable);
        return text.toString();
    }

    private void append(StringBuilder text, Throwable throwable) {
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
}

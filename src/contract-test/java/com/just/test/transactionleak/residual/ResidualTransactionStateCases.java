package com.just.test.transactionleak.residual;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import com.just.test.transactionleak.TransactionTestSupport.TestConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertFalse;

@JustTest
@Execution(ExecutionMode.SAME_THREAD)
@ContextConfiguration(classes = TestConfiguration.class)
public class ResidualTransactionStateCases implements JustTestLifecycle {

    private static final Object UNKNOWN_RESOURCE_KEY = new Object();

    @BeforeAll
    static void leaveResidualStateForBeforeEachSafetyNet() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.bindResource(UNKNOWN_RESOURCE_KEY, new Object());
    }

    @CaseSource
    void run(CaseContext context) {
        throw new AssertionError("case body must not run with residual transaction state");
    }

    @AfterAll
    static void transactionThreadStateWasQuarantined() {
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
        assertFalse(TransactionSynchronizationManager.hasResource(UNKNOWN_RESOURCE_KEY));
    }
}

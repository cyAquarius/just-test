package com.just.test.transactionleak.control;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import com.just.test.transactionleak.TransactionTestSupport.ExpectedBusinessException;
import com.just.test.transactionleak.TransactionTestSupport.RecordService;
import com.just.test.transactionleak.TransactionTestSupport.TestConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@JustTest
@Execution(ExecutionMode.SAME_THREAD)
@ContextConfiguration(classes = TestConfiguration.class)
public class TransactionRollbackControlCases implements JustTestLifecycle {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private RecordService recordService;

    @CaseSource
    void run(CaseContext context) {
        if ("a-rollback".equals(context.getCaseName())) {
            TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
            try {
                throw new ExpectedBusinessException("validation failed after transaction start");
            } finally {
                transactionManager.rollback(status);
            }
        }

        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals("after-rollback", recordService.insertAndFind("after-rollback"));
    }

    @AfterAll
    static void transactionThreadStateIsClean() {
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

}

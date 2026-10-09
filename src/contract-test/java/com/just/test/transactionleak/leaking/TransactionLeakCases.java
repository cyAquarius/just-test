package com.just.test.transactionleak.leaking;

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
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JustTest
@Execution(ExecutionMode.SAME_THREAD)
@ContextConfiguration(classes = TestConfiguration.class)
public class TransactionLeakCases implements JustTestLifecycle {

    private static final Object UNKNOWN_RESOURCE_KEY = new Object();
    private static Connection connectionUnderUnknownKey;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private RecordService recordService;

    @Autowired
    private DataSource dataSource;

    @CaseSource
    void run(CaseContext context) {
        if ("a-leak".equals(context.getCaseName())) {
            transactionManager.getTransaction(new DefaultTransactionDefinition());
            try {
                connectionUnderUnknownKey = dataSource.getConnection();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            TransactionSynchronizationManager.bindResource(
                    UNKNOWN_RESOURCE_KEY, connectionUnderUnknownKey);
            throw new ExpectedBusinessException("validation failed after transaction start");
        }

        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals("after-leak", recordService.insertAndFind("after-leak"));
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        try {
            assertTrue(connectionUnderUnknownKey.isClosed());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @AfterAll
    static void transactionThreadStateIsClean() {
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

}

package com.just.test.smarttest.demo;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.lifecycle.SmartTestLifecycle;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SmartTest
@Execution(ExecutionMode.CONCURRENT)
@ContextConfiguration(classes = ConcurrentCasesSmartTest.EmptyConfiguration.class)
class ConcurrentCasesSmartTest implements SmartTestLifecycle {

    private static final AtomicInteger ACTIVE_CASES = new AtomicInteger();
    private static final AtomicInteger MAX_ACTIVE_CASES = new AtomicInteger();
    private static final Set<String> EXECUTION_THREADS = ConcurrentHashMap.newKeySet();
    private static final CyclicBarrier CASE_START_BARRIER = new CyclicBarrier(2);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @CaseSource
    void keepsEachConcurrentCaseInItsOwnDatabase(CaseContext context) throws Exception {
        int active = ACTIVE_CASES.incrementAndGet();
        MAX_ACTIVE_CASES.accumulateAndGet(active, Math::max);
        EXECUTION_THREADS.add(Thread.currentThread().getName());
        try {
            CASE_START_BARRIER.await(5, TimeUnit.SECONDS);
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM parallel_case_record", Integer.class));
            assertEquals(context.getString("marker"), jdbcTemplate.queryForObject(
                    "SELECT marker FROM parallel_case_record WHERE id = 1", String.class));
        } finally {
            ACTIVE_CASES.decrementAndGet();
        }
    }

    @AfterAll
    static void verifiesCasesActuallyOverlapped() {
        assertTrue(MAX_ACTIVE_CASES.get() >= 2,
                "Concurrent case test did not execute overlapping invocations");
        assertTrue(EXECUTION_THREADS.size() >= 2,
                "Concurrent case test used only one worker thread");
    }

    @Configuration
    static class EmptyConfiguration {
    }
}

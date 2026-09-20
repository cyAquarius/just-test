package com.just.test.demo.concurrent;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
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

@JustTest
@Execution(ExecutionMode.CONCURRENT)
@ContextConfiguration(classes = ConcurrentCasesJustTest.EmptyConfiguration.class)
class ConcurrentCasesJustTest implements JustTestLifecycle {

    private static final AtomicInteger ACTIVE_CASES = new AtomicInteger();
    private static final AtomicInteger MAX_ACTIVE_CASES = new AtomicInteger();
    private static final Set<String> EXECUTION_THREADS = ConcurrentHashMap.newKeySet();
    /**
     * Schema clone 会在 DataSource 上串行化建库。等待窗口与并行类契约测试对齐：
     * 2 方（与 fixed parallelism=2 一致）、30 秒，避免短超时 flake。
     */
    private static final int OVERLAP_PARTY_COUNT = 2;
    private static final long OVERLAP_TIMEOUT_SECONDS = 30L;
    private static final CyclicBarrier CASE_START_BARRIER = new CyclicBarrier(OVERLAP_PARTY_COUNT);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @CaseSource
    void keepsEachConcurrentCaseInItsOwnDatabase(CaseContext context) throws Exception {
        int active = ACTIVE_CASES.incrementAndGet();
        MAX_ACTIVE_CASES.accumulateAndGet(active, Math::max);
        EXECUTION_THREADS.add(Thread.currentThread().getName());
        try {
            CASE_START_BARRIER.await(OVERLAP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
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

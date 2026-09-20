package com.just.test.parallel;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ParallelClassCoordinator {

    private static final AtomicInteger ACTIVE_CLASSES = new AtomicInteger();
    private static final AtomicInteger MAX_ACTIVE_CLASSES = new AtomicInteger();
    private static final Set<String> EXECUTION_THREADS = ConcurrentHashMap.newKeySet();
    private static final int OVERLAP_PARTY_COUNT = 2;
    private static final long OVERLAP_TIMEOUT_SECONDS = 30L;
    private static final CyclicBarrier CLASS_START_BARRIER = new CyclicBarrier(OVERLAP_PARTY_COUNT);

    private ParallelClassCoordinator() {
    }

    public static void overlap(String expectedMarker, String actualMarker) throws Exception {
        int active = ACTIVE_CLASSES.incrementAndGet();
        MAX_ACTIVE_CLASSES.accumulateAndGet(active, Math::max);
        EXECUTION_THREADS.add(Thread.currentThread().getName());
        try {
            CLASS_START_BARRIER.await(OVERLAP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertEquals(expectedMarker, actualMarker);
        } finally {
            ACTIVE_CLASSES.decrementAndGet();
        }
    }

    public static void assertClassesOverlapped() {
        assertTrue(MAX_ACTIVE_CLASSES.get() >= 2,
                "JustTest classes did not execute overlapping cases");
        assertTrue(EXECUTION_THREADS.size() >= 2,
                "JustTest classes used only one worker thread");
    }
}

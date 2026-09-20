package com.just.test.internal.scope;

import com.just.test.context.CaseContext;
import com.just.test.internal.context.CaseExecutionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadScopeTest {

    @BeforeEach
    void bindCase() {
        CaseExecutionContext.bind(new CaseContext("scope-test", "scope-test"));
    }

    @AfterEach
    void clearCase() {
        ThreadScope.clearCurrentThread();
        CaseExecutionContext.clear();
    }

    @Test
    void resetDestroysAndRemovesCurrentThreadBeans() {
        ThreadScope scope = new ThreadScope();
        AtomicBoolean destroyed = new AtomicBoolean();
        Object first = scope.get("sample", Object::new);
        scope.registerDestructionCallback("sample", () -> destroyed.set(true));

        ThreadScope.resetCurrentThread();

        Object second = scope.get("sample", Object::new);
        assertTrue(destroyed.get());
        assertNotSame(first, second);
        ThreadScope.clearCurrentThread();
    }

    @Test
    void resetRunsAllCallbacksAndKeepsLaterFailuresSuppressed() {
        ThreadScope scope = new ThreadScope();
        AtomicInteger callbackCount = new AtomicInteger();
        scope.get("first", Object::new);
        scope.get("second", Object::new);
        scope.registerDestructionCallback("first", () -> {
            callbackCount.incrementAndGet();
            throw new IllegalStateException("first");
        });
        scope.registerDestructionCallback("second", () -> {
            callbackCount.incrementAndGet();
            throw new IllegalArgumentException("second");
        });

        RuntimeException failure = assertThrows(RuntimeException.class, ThreadScope::resetCurrentThread);

        assertTrue(callbackCount.get() == 2);
        assertTrue(failure.getSuppressed().length == 1);
    }

    @Test
    void removingBeanAlsoRemovesItsDestructionCallback() {
        ThreadScope scope = new ThreadScope();
        AtomicBoolean destroyed = new AtomicBoolean();
        scope.get("sample", Object::new);
        scope.registerDestructionCallback("sample", () -> destroyed.set(true));

        scope.remove("sample");
        ThreadScope.resetCurrentThread();

        assertTrue(!destroyed.get());
    }

    @Test
    void isolatesSameBeanNameAcrossScopeInstancesOnOneThread() {
        ThreadScope firstScope = new ThreadScope();
        ThreadScope secondScope = new ThreadScope();

        Object first = firstScope.get("client", Object::new);
        Object second = secondScope.get("client", Object::new);

        assertNotSame(first, second);
    }

    @Test
    void rejectsAsyncAccessBeforeCreatingBean() throws Exception {
        ThreadScope scope = new ThreadScope();
        AtomicInteger creations = new AtomicInteger();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Throwable> result = executor.submit(() -> {
                try {
                    scope.get("client", () -> {
                        creations.incrementAndGet();
                        return new Object();
                    });
                    return null;
                } catch (Throwable failure) {
                    return failure;
                }
            });

            Throwable failure = result.get();
            assertTrue(failure instanceof IllegalStateException);
            assertTrue(failure.getMessage().contains("requires an active JustTest case"));
            assertTrue(creations.get() == 0);
        } finally {
            executor.shutdownNow();
        }
    }
}

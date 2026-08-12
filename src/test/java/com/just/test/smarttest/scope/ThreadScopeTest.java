package com.just.test.smarttest.scope;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadScopeTest {

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
}

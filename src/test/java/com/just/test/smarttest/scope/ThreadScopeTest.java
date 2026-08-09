package com.just.test.smarttest.scope;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertNotSame;
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
}

package com.just.test.lifecycle.customexception;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import org.junit.jupiter.api.AfterAll;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@JustTest
@ContextConfiguration(classes = CustomExceptionVerifierJustTest.EmptyConfiguration.class)
class CustomExceptionVerifierJustTest implements JustTestLifecycle {

    private static final AtomicInteger VERIFICATIONS = new AtomicInteger();
    private static final AtomicInteger AFTER_EXECUTE = new AtomicInteger();

    @Override
    public void afterExecute(CaseContext context) {
        AFTER_EXECUTE.incrementAndGet();
    }

    @Override
    public boolean verifyException(CaseContext context) {
        if (AFTER_EXECUTE.get() == 0) {
            throw new AssertionError("afterExecute must run before verifyException when the test throws");
        }
        VERIFICATIONS.incrementAndGet();
        return context.getException() instanceof IllegalStateException;
    }

    @CaseSource
    void acceptsExceptionWithoutYaml(CaseContext context) {
        throw new IllegalStateException("accepted by custom verifier");
    }

    @AfterAll
    static void verifiesExactlyOnce() {
        assertEquals(1, VERIFICATIONS.get());
        assertEquals(1, AFTER_EXECUTE.get());
    }

    @Configuration
    static class EmptyConfiguration {
    }
}

package com.just.test.smarttest.lifecycle.customexception;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.lifecycle.SmartTestLifecycle;
import org.junit.jupiter.api.AfterAll;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SmartTest
@ContextConfiguration(classes = CustomExceptionVerifierSmartTest.EmptyConfiguration.class)
class CustomExceptionVerifierSmartTest implements SmartTestLifecycle {

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

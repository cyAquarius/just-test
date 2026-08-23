package com.just.test.smarttest.lifecycle;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;
import org.junit.jupiter.api.AfterAll;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SmartTest
@ContextConfiguration(classes = CustomExceptionVerifierSmartTest.EmptyConfiguration.class)
class CustomExceptionVerifierSmartTest implements SmartTestLifecycle {

    private static final AtomicInteger VERIFICATIONS = new AtomicInteger();

    @Override
    public boolean verifyException(CaseContext context) {
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
    }

    @Configuration
    static class EmptyConfiguration {
    }
}

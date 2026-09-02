package com.just.test.smarttest.internal.mock;

import com.just.test.smarttest.annotation.SmartMock;
import com.just.test.smarttest.annotation.SmartTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextCustomizer;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartMockContextCustomizerFactoryTest {

    private final SmartMockContextCustomizerFactory factory = new SmartMockContextCustomizerFactory();

    @Test
    void rejectsSmartMockOutsideSmartTest() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> factory.createContextCustomizer(InvalidTest.class, Collections.emptyList()));

        assertTrue(failure.getMessage().contains("uses @SmartMock without @SmartTest"));
    }

    @Test
    void leavesOrdinaryTestsWithoutSmartMockUntouched() {
        assertNull(factory.createContextCustomizer(OrdinaryTest.class, Collections.emptyList()));
    }

    @Test
    void configuresSmartTestWithoutSmartMockFields() {
        ContextCustomizer customizer = factory.createContextCustomizer(
                EmptySmartTest.class, Collections.emptyList());

        assertNotNull(customizer);
    }

    static class InvalidTest {
        @SmartMock
        private Runnable client;
    }

    static class OrdinaryTest {
    }

    @SmartTest
    static class EmptySmartTest {
    }
}

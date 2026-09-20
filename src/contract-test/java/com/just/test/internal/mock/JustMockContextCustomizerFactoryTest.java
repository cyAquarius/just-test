package com.just.test.internal.mock;

import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextCustomizer;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JustMockContextCustomizerFactoryTest {

    private final JustMockContextCustomizerFactory factory = new JustMockContextCustomizerFactory();

    @Test
    void rejectsJustMockOutsideJustTest() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> factory.createContextCustomizer(InvalidTest.class, Collections.emptyList()));

        assertTrue(failure.getMessage().contains("uses @JustMock without @JustTest"));
    }

    @Test
    void leavesOrdinaryTestsWithoutJustMockUntouched() {
        assertNull(factory.createContextCustomizer(OrdinaryTest.class, Collections.emptyList()));
    }

    @Test
    void configuresJustTestWithoutJustMockFields() {
        ContextCustomizer customizer = factory.createContextCustomizer(
                EmptyJustTest.class, Collections.emptyList());

        assertNotNull(customizer);
    }

    static class InvalidTest {
        @JustMock
        private Runnable client;
    }

    static class OrdinaryTest {
    }

    @JustTest
    static class EmptyJustTest {
    }
}

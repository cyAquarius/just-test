package com.just.test.internal.mock;

import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustSpy;
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
    void rejectsJustSpyOutsideJustTest() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> factory.createContextCustomizer(SpyWithoutJustTest.class, Collections.emptyList()));

        assertTrue(failure.getMessage().contains("uses @JustSpy without @JustTest"));
    }

    @Test
    void rejectsFieldThatDeclaresBothJustMockAndJustSpy() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> factory.createContextCustomizer(BothAnnotations.class, Collections.emptyList()));

        assertTrue(failure.getMessage().contains("declares both @JustMock and @JustSpy"));
        assertTrue(failure.getMessage().contains("client"));
    }

    @Test
    void rejectsPrimitiveAndArrayMockFields() {
        IllegalStateException primitive = assertThrows(IllegalStateException.class,
                () -> factory.createContextCustomizer(PrimitiveJustTest.class, Collections.emptyList()));
        assertTrue(primitive.getMessage().contains("count"));
        assertTrue(primitive.getMessage().contains("int"));
        assertTrue(primitive.getMessage().contains("primitive"));

        IllegalStateException array = assertThrows(IllegalStateException.class,
                () -> factory.createContextCustomizer(ArraySpyTest.class, Collections.emptyList()));
        assertTrue(array.getMessage().contains("payload"));
        assertTrue(array.getMessage().contains("array"));
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

    static class SpyWithoutJustTest {
        @JustSpy
        private Runnable client;
    }

    @JustTest
    static class BothAnnotations {
        @JustMock
        @JustSpy
        private Runnable client;
    }

    static class OrdinaryTest {
    }

    @JustTest
    static class EmptyJustTest {
    }

    @JustTest
    static class PrimitiveJustTest {
        @JustMock
        private int count;
    }

    @JustTest
    static class ArraySpyTest {
        @JustSpy
        private byte[] payload;
    }
}

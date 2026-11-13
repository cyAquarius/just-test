package com.just.test.internal.mock;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JustMockKnownSourcesTest {

    @Test
    void cacheKeyIncludesClassLoaderIdentity() {
        Set<Class<?>> roots = Collections.<Class<?>>singleton(JustMockKnownSourcesTest.class);
        String[] profiles = new String[] {"test"};
        ClassLoader first = new ClassLoader(getClass().getClassLoader()) {
        };
        ClassLoader second = new ClassLoader(getClass().getClassLoader()) {
        };

        String keyA = JustMockKnownSources.cacheKey(first, roots, profiles);
        String keyB = JustMockKnownSources.cacheKey(second, roots, profiles);

        assertFalse(keyA.equals(keyB));
        assertEquals(keyA, JustMockKnownSources.cacheKey(first, roots, profiles));
    }
}

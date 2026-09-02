package com.just.test.smarttest.internal.lifecycle;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.lifecycle.SmartTestLifecycle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartTestClassValidatorTest {

    @Test
    void acceptsLifecycleAndCaseSourceOnlyClass() {
        assertDoesNotThrow(() -> SmartTestClassValidator.validate(ValidLifecycle.class));
    }

    @Test
    void failsInSharedValidatorWhenLifecycleIsMissing() {
        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> SmartTestClassValidator.validate(MissingLifecycle.class));

        assertTrue(failure.getMessage().contains("must implement SmartTestLifecycle"));
        assertTrue(failure.getMessage().contains(MissingLifecycle.class.getName()));
    }

    static class ValidLifecycle implements SmartTestLifecycle {
        @CaseSource
        void smartCase() {
        }
    }

    static class MissingLifecycle {
        @CaseSource
        void smartCase() {
        }
    }
}

package com.just.test.smarttest.internal.lifecycle;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.lifecycle.SmartTestLifecycle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void rejectsPerClassCombinedWithClassLevelConcurrentExecution() {
        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> SmartTestClassValidator.validate(PerClassConcurrent.class));

        assertTrue(failure.getMessage().contains("@TestInstance(PER_CLASS)"));
        assertTrue(failure.getMessage().contains("concurrent case execution"));
        assertTrue(failure.getMessage().contains("@SmartMock"));
        assertTrue(failure.getMessage().contains("PER_METHOD"));
        assertTrue(failure.getMessage().contains("SAME_THREAD"));
    }

    @Test
    void rejectsPerClassCombinedWithMethodLevelConcurrentExecution() {
        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> SmartTestClassValidator.validate(PerClassMethodConcurrent.class));

        assertTrue(failure.getMessage().contains("@TestInstance(PER_CLASS)"));
        assertTrue(failure.getMessage().contains("concurrent case execution"));
    }

    @Test
    void acceptsPerClassWithSameThreadExecution() {
        assertDoesNotThrow(() -> SmartTestClassValidator.validate(PerClassSameThread.class));
    }

    @Test
    void acceptsPerMethodWithConcurrentExecution() {
        assertDoesNotThrow(() -> SmartTestClassValidator.validate(PerMethodConcurrent.class));
    }

    @Test
    void acceptsPerClassWhenAllCaseMethodsStaySameThread() {
        assertDoesNotThrow(() -> SmartTestClassValidator.validate(PerClassConcurrentClassSameThreadMethod.class));
    }

    @Test
    void rejectsPerClassWhenConfigurationDefaultIsConcurrent() {
        ExtensionContext context = mock(ExtensionContext.class);
        when(context.getTestInstanceLifecycle())
                .thenReturn(Optional.of(TestInstance.Lifecycle.PER_CLASS));
        when(context.getConfigurationParameter(SmartTestClassValidator.PARALLEL_ENABLED_PROPERTY))
                .thenReturn(Optional.of("true"));
        when(context.getConfigurationParameter(SmartTestClassValidator.PARALLEL_MODE_DEFAULT_PROPERTY))
                .thenReturn(Optional.of("concurrent"));

        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> SmartTestClassValidator.validate(PerClassNoExecution.class, context));

        assertTrue(failure.getMessage().contains("@TestInstance(PER_CLASS)"));
        verify(context, never()).getExecutionMode();
    }

    @Test
    void acceptsPerClassWhenParallelDisabledEvenIfModeDefaultIsConcurrent() {
        ExtensionContext context = mock(ExtensionContext.class);
        when(context.getTestInstanceLifecycle())
                .thenReturn(Optional.of(TestInstance.Lifecycle.PER_CLASS));
        when(context.getConfigurationParameter(SmartTestClassValidator.PARALLEL_ENABLED_PROPERTY))
                .thenReturn(Optional.of("false"));
        when(context.getConfigurationParameter(SmartTestClassValidator.PARALLEL_MODE_DEFAULT_PROPERTY))
                .thenReturn(Optional.of("concurrent"));

        assertDoesNotThrow(() -> SmartTestClassValidator.validate(PerClassNoExecution.class, context));
        verify(context, never()).getExecutionMode();
    }

    @Test
    void acceptsPerClassConcurrentAnnotationWhenParallelIsDisabled() {
        ExtensionContext context = mock(ExtensionContext.class);
        when(context.getTestInstanceLifecycle())
                .thenReturn(Optional.of(TestInstance.Lifecycle.PER_CLASS));
        when(context.getConfigurationParameter(SmartTestClassValidator.PARALLEL_ENABLED_PROPERTY))
                .thenReturn(Optional.of("false"));

        assertDoesNotThrow(() -> SmartTestClassValidator.validate(PerClassConcurrent.class, context));
        verify(context, never()).getExecutionMode();
    }

    @Test
    void doesNotTreatClassLevelExecutionModeAsCaseConcurrency() {
        ExtensionContext context = mock(ExtensionContext.class);
        when(context.getTestInstanceLifecycle())
                .thenReturn(Optional.of(TestInstance.Lifecycle.PER_CLASS));
        when(context.getConfigurationParameter(SmartTestClassValidator.PARALLEL_ENABLED_PROPERTY))
                .thenReturn(Optional.of("true"));
        when(context.getConfigurationParameter(SmartTestClassValidator.PARALLEL_MODE_DEFAULT_PROPERTY))
                .thenReturn(Optional.of("same_thread"));
        when(context.getExecutionMode()).thenReturn(ExecutionMode.CONCURRENT);

        assertDoesNotThrow(() -> SmartTestClassValidator.validate(PerClassNoExecution.class, context));
        verify(context, never()).getExecutionMode();
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

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Execution(ExecutionMode.CONCURRENT)
    static class PerClassConcurrent implements SmartTestLifecycle {
        @CaseSource
        void smartCase() {
        }
    }

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    static class PerClassMethodConcurrent implements SmartTestLifecycle {
        @CaseSource
        @Execution(ExecutionMode.CONCURRENT)
        void smartCase() {
        }
    }

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Execution(ExecutionMode.SAME_THREAD)
    static class PerClassSameThread implements SmartTestLifecycle {
        @CaseSource
        void smartCase() {
        }
    }

    @Execution(ExecutionMode.CONCURRENT)
    static class PerMethodConcurrent implements SmartTestLifecycle {
        @CaseSource
        void smartCase() {
        }
    }

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Execution(ExecutionMode.CONCURRENT)
    static class PerClassConcurrentClassSameThreadMethod implements SmartTestLifecycle {
        @CaseSource
        @Execution(ExecutionMode.SAME_THREAD)
        void smartCase() {
        }
    }

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    static class PerClassNoExecution implements SmartTestLifecycle {
        @CaseSource
        void smartCase() {
        }
    }
}

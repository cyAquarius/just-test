package com.just.test.internal.lifecycle;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustSpy;
import com.just.test.lifecycle.JustTestLifecycle;
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

class JustTestClassValidatorTest {

    @Test
    void acceptsLifecycleAndCaseSourceOnlyClass() {
        assertDoesNotThrow(() -> JustTestClassValidator.validate(ValidLifecycle.class));
    }

    @Test
    void failsInSharedValidatorWhenLifecycleIsMissing() {
        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> JustTestClassValidator.validate(MissingLifecycle.class));

        assertTrue(failure.getMessage().contains("must implement JustTestLifecycle"));
        assertTrue(failure.getMessage().contains(MissingLifecycle.class.getName()));
    }

    @Test
    void rejectsPerClassCombinedWithClassLevelConcurrentExecution() {
        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> JustTestClassValidator.validate(PerClassConcurrent.class));

        assertTrue(failure.getMessage().contains("@TestInstance(PER_CLASS)"));
        assertTrue(failure.getMessage().contains("concurrent case execution"));
        assertTrue(failure.getMessage().contains("@JustMock"));
        assertTrue(failure.getMessage().contains("PER_METHOD"));
        assertTrue(failure.getMessage().contains("SAME_THREAD"));
    }

    @Test
    void rejectsPerClassCombinedWithMethodLevelConcurrentExecution() {
        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> JustTestClassValidator.validate(PerClassMethodConcurrent.class));

        assertTrue(failure.getMessage().contains("@TestInstance(PER_CLASS)"));
        assertTrue(failure.getMessage().contains("concurrent case execution"));
    }

    @Test
    void acceptsPerClassWithSameThreadExecution() {
        assertDoesNotThrow(() -> JustTestClassValidator.validate(PerClassSameThread.class));
    }

    @Test
    void acceptsPerMethodWithConcurrentExecution() {
        assertDoesNotThrow(() -> JustTestClassValidator.validate(PerMethodConcurrent.class));
    }

    @Test
    void acceptsPerClassWhenAllCaseMethodsStaySameThread() {
        assertDoesNotThrow(() -> JustTestClassValidator.validate(PerClassConcurrentClassSameThreadMethod.class));
    }

    @Test
    void rejectsPerClassWhenConfigurationDefaultIsConcurrent() {
        ExtensionContext context = mock(ExtensionContext.class);
        when(context.getTestInstanceLifecycle())
                .thenReturn(Optional.of(TestInstance.Lifecycle.PER_CLASS));
        when(context.getConfigurationParameter(JustTestClassValidator.PARALLEL_ENABLED_PROPERTY))
                .thenReturn(Optional.of("true"));
        when(context.getConfigurationParameter(JustTestClassValidator.PARALLEL_MODE_DEFAULT_PROPERTY))
                .thenReturn(Optional.of("concurrent"));

        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> JustTestClassValidator.validate(PerClassNoExecution.class, context));

        assertTrue(failure.getMessage().contains("@TestInstance(PER_CLASS)"));
        verify(context, never()).getExecutionMode();
    }

    @Test
    void acceptsPerClassWhenParallelDisabledEvenIfModeDefaultIsConcurrent() {
        ExtensionContext context = mock(ExtensionContext.class);
        when(context.getTestInstanceLifecycle())
                .thenReturn(Optional.of(TestInstance.Lifecycle.PER_CLASS));
        when(context.getConfigurationParameter(JustTestClassValidator.PARALLEL_ENABLED_PROPERTY))
                .thenReturn(Optional.of("false"));
        when(context.getConfigurationParameter(JustTestClassValidator.PARALLEL_MODE_DEFAULT_PROPERTY))
                .thenReturn(Optional.of("concurrent"));

        assertDoesNotThrow(() -> JustTestClassValidator.validate(PerClassNoExecution.class, context));
        verify(context, never()).getExecutionMode();
    }

    @Test
    void acceptsPerClassConcurrentAnnotationWhenParallelIsDisabled() {
        ExtensionContext context = mock(ExtensionContext.class);
        when(context.getTestInstanceLifecycle())
                .thenReturn(Optional.of(TestInstance.Lifecycle.PER_CLASS));
        when(context.getConfigurationParameter(JustTestClassValidator.PARALLEL_ENABLED_PROPERTY))
                .thenReturn(Optional.of("false"));

        assertDoesNotThrow(() -> JustTestClassValidator.validate(PerClassConcurrent.class, context));
        verify(context, never()).getExecutionMode();
    }

    @Test
    void rejectsStaticJustMockField() {
        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> JustTestClassValidator.validate(StaticJustMockField.class));

        assertTrue(failure.getMessage().contains("sharedGateway"));
        assertTrue(failure.getMessage().contains("breaks thread isolation"));
        assertTrue(failure.getMessage().contains("@JustMock"));
        assertTrue(failure.getMessage().contains(StaticJustMockField.class.getName()));
    }

    @Test
    void rejectsStaticJustSpyField() {
        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> JustTestClassValidator.validate(StaticJustSpyField.class));

        assertTrue(failure.getMessage().contains("sharedClock"));
        assertTrue(failure.getMessage().contains("breaks thread isolation"));
        assertTrue(failure.getMessage().contains("@JustSpy"));
    }

    @Test
    void rejectsInheritedStaticJustMockField() {
        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> JustTestClassValidator.validate(InheritedStaticJustMock.class));

        assertTrue(failure.getMessage().contains("inheritedGateway"));
        assertTrue(failure.getMessage().contains(StaticMockBase.class.getName() + ".inheritedGateway"));
        assertTrue(failure.getMessage().contains("breaks thread isolation"));
    }

    @Test
    void acceptsInstanceMockAndSpyFields() {
        assertDoesNotThrow(() -> JustTestClassValidator.validate(InstanceMockAndSpyFields.class));
        assertDoesNotThrow(() -> JustTestClassValidator.validate(InheritedInstanceMock.class));
    }

    @Test
    void doesNotTreatClassLevelExecutionModeAsCaseConcurrency() {
        ExtensionContext context = mock(ExtensionContext.class);
        when(context.getTestInstanceLifecycle())
                .thenReturn(Optional.of(TestInstance.Lifecycle.PER_CLASS));
        when(context.getConfigurationParameter(JustTestClassValidator.PARALLEL_ENABLED_PROPERTY))
                .thenReturn(Optional.of("true"));
        when(context.getConfigurationParameter(JustTestClassValidator.PARALLEL_MODE_DEFAULT_PROPERTY))
                .thenReturn(Optional.of("same_thread"));
        when(context.getExecutionMode()).thenReturn(ExecutionMode.CONCURRENT);

        assertDoesNotThrow(() -> JustTestClassValidator.validate(PerClassNoExecution.class, context));
        verify(context, never()).getExecutionMode();
    }

    static class ValidLifecycle implements JustTestLifecycle {
        @CaseSource
        void justCase() {
        }
    }

    static class MissingLifecycle {
        @CaseSource
        void justCase() {
        }
    }

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Execution(ExecutionMode.CONCURRENT)
    static class PerClassConcurrent implements JustTestLifecycle {
        @CaseSource
        void justCase() {
        }
    }

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    static class PerClassMethodConcurrent implements JustTestLifecycle {
        @CaseSource
        @Execution(ExecutionMode.CONCURRENT)
        void justCase() {
        }
    }

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Execution(ExecutionMode.SAME_THREAD)
    static class PerClassSameThread implements JustTestLifecycle {
        @CaseSource
        void justCase() {
        }
    }

    @Execution(ExecutionMode.CONCURRENT)
    static class PerMethodConcurrent implements JustTestLifecycle {
        @CaseSource
        void justCase() {
        }
    }

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Execution(ExecutionMode.CONCURRENT)
    static class PerClassConcurrentClassSameThreadMethod implements JustTestLifecycle {
        @CaseSource
        @Execution(ExecutionMode.SAME_THREAD)
        void justCase() {
        }
    }

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    static class PerClassNoExecution implements JustTestLifecycle {
        @CaseSource
        void justCase() {
        }
    }

    static class StaticJustMockField implements JustTestLifecycle {
        @JustMock
        static SampleClient sharedGateway;

        @CaseSource
        void justCase() {
        }
    }

    static class StaticJustSpyField implements JustTestLifecycle {
        @JustSpy
        private static SampleClock sharedClock;

        @CaseSource
        void justCase() {
        }
    }

    static class StaticMockBase {
        @JustMock
        static SampleClient inheritedGateway;
    }

    static class InheritedStaticJustMock extends StaticMockBase implements JustTestLifecycle {
        @JustMock
        SampleClient instanceGateway;

        @CaseSource
        void justCase() {
        }
    }

    static class InstanceMockAndSpyFields implements JustTestLifecycle {
        static SampleClient plainStatic;

        @JustMock
        SampleClient gateway;

        @JustSpy
        SampleClock clock;

        @CaseSource
        void justCase() {
        }
    }

    static class InstanceMockBase {
        @JustMock
        SampleClient gateway;
    }

    static class InheritedInstanceMock extends InstanceMockBase implements JustTestLifecycle {
        @CaseSource
        void justCase() {
        }
    }

    interface SampleClient {
        String name();
    }

    static class SampleClock {
        String now() {
            return "real";
        }
    }
}

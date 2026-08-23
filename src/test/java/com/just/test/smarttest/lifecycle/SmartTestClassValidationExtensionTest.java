package com.just.test.smarttest.lifecycle;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartTestClassValidationExtensionTest {

    @Test
    void smartTestRunsValidationBeforeSpringExtension() {
        ExtendWith extendWith = SmartTest.class.getAnnotation(ExtendWith.class);

        assertArrayEquals(new Class<?>[]{
                SmartTestClassValidationExtension.class,
                SpringExtension.class
        }, extendWith.value());
    }

    @Test
    void acceptsCaseSourceOnlyClass() {
        assertDoesNotThrow(() -> SmartTestClassValidationExtension.validateTestClass(CaseSourceOnly.class));
    }

    @Test
    void rejectsOrdinaryAndRepeatedTestsWithMigrationGuidance() {
        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> SmartTestClassValidationExtension.validateTestClass(MixedTests.class));

        assertTrue(failure.getMessage().contains("ordinaryTest, repeatedTest"));
        assertTrue(failure.getMessage().contains("may only contain @CaseSource"));
        assertTrue(failure.getMessage().contains("separate test class"));
    }

    static class CaseSourceOnly {
        @CaseSource
        void smartCase() {
        }
    }

    static class MixedTests {
        @CaseSource
        void smartCase() {
        }

        @Test
        void ordinaryTest() {
        }

        @RepeatedTest(2)
        void repeatedTest() {
        }
    }
}

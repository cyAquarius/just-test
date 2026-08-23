package com.just.test.smarttest.lifecycle;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTestContextBootstrapper;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

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

    @Test
    void rejectsCompetingTestAnnotationsOnCaseSourceMethod() {
        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> SmartTestClassValidationExtension.validateTestClass(CompetingAnnotations.class));

        assertTrue(failure.getMessage().contains("competing JUnit test annotations"));
        assertTrue(failure.getMessage().contains("@CaseSource"));
        assertTrue(failure.getMessage().contains("@Test"));
    }

    @Test
    void rejectsSpringLifecycleAnnotationsThatRunBeforeCaseBinding() {
        ExtensionConfigurationException transactional = assertThrows(ExtensionConfigurationException.class,
                () -> SmartTestClassValidationExtension.validateTestClass(TransactionalCase.class));
        ExtensionConfigurationException sql = assertThrows(ExtensionConfigurationException.class,
                () -> SmartTestClassValidationExtension.validateTestClass(SqlCase.class));
        ExtensionConfigurationException classLevel = assertThrows(ExtensionConfigurationException.class,
                () -> SmartTestClassValidationExtension.validateTestClass(TransactionalClass.class));

        assertTrue(transactional.getMessage().contains("@Transactional or @Sql"));
        assertTrue(sql.getMessage().contains("@Transactional or @Sql"));
        assertTrue(classLevel.getMessage().contains("class-level @Transactional or @Sql"));
    }

    @Test
    void rejectsRedundantSpringBootTestBootstrapper() {
        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> SmartTestClassValidationExtension.validateTestClass(RedundantBootTest.class));

        assertTrue(failure.getMessage().contains("must not combine @SmartTest with @SpringBootTest"));
    }

    @Test
    void rejectsAnotherExplicitBootstrapper() {
        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> SmartTestClassValidationExtension.validateTestClass(RedundantBootstrapper.class));

        assertTrue(failure.getMessage().contains("must not declare another @BootstrapWith"));
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

    static class CompetingAnnotations {
        @Test
        @CaseSource
        void mixed() {
        }
    }

    static class TransactionalCase {
        @Transactional
        @CaseSource
        void smartCase() {
        }
    }

    static class SqlCase {
        @Sql
        @CaseSource
        void smartCase() {
        }
    }

    @SmartTest
    @Transactional
    static class TransactionalClass {
        @CaseSource
        void smartCase() {
        }
    }

    @SmartTest
    @SpringBootTest
    static class RedundantBootTest {
        @CaseSource
        void smartCase() {
        }
    }

    @SmartTest
    @BootstrapWith(SpringBootTestContextBootstrapper.class)
    static class RedundantBootstrapper {
        @CaseSource
        void smartCase() {
        }
    }
}

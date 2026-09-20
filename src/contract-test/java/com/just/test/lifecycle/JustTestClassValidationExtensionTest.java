package com.just.test.lifecycle;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
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

class JustTestClassValidationExtensionTest {

    @Test
    void justTestRunsValidationBeforeSpringExtension() {
        ExtendWith extendWith = JustTest.class.getAnnotation(ExtendWith.class);

        assertArrayEquals(new Class<?>[]{
                JustTestClassValidationExtension.class,
                SpringExtension.class
        }, extendWith.value());
    }

    @Test
    void rejectsClassThatDoesNotImplementLifecycle() {
        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> JustTestClassValidationExtension.validateTestClass(MissingLifecycle.class));

        assertTrue(failure.getMessage().contains("must implement JustTestLifecycle"));
    }

    @Test
    void acceptsCaseSourceOnlyClass() {
        assertDoesNotThrow(() -> JustTestClassValidationExtension.validateTestClass(CaseSourceOnly.class));
    }

    @Test
    void rejectsOrdinaryAndRepeatedTestsWithMigrationGuidance() {
        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> JustTestClassValidationExtension.validateTestClass(MixedTests.class));

        assertTrue(failure.getMessage().contains("ordinaryTest, repeatedTest"));
        assertTrue(failure.getMessage().contains("may only contain @CaseSource"));
        assertTrue(failure.getMessage().contains("separate test class"));
    }

    @Test
    void rejectsCompetingTestAnnotationsOnCaseSourceMethod() {
        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> JustTestClassValidationExtension.validateTestClass(CompetingAnnotations.class));

        assertTrue(failure.getMessage().contains("competing JUnit test annotations"));
        assertTrue(failure.getMessage().contains("@CaseSource"));
        assertTrue(failure.getMessage().contains("@Test"));
    }

    @Test
    void rejectsSpringLifecycleAnnotationsThatRunBeforeCaseBinding() {
        ExtensionConfigurationException transactional = assertThrows(ExtensionConfigurationException.class,
                () -> JustTestClassValidationExtension.validateTestClass(TransactionalCase.class));
        ExtensionConfigurationException sql = assertThrows(ExtensionConfigurationException.class,
                () -> JustTestClassValidationExtension.validateTestClass(SqlCase.class));
        ExtensionConfigurationException classLevel = assertThrows(ExtensionConfigurationException.class,
                () -> JustTestClassValidationExtension.validateTestClass(TransactionalClass.class));

        assertTrue(transactional.getMessage().contains("@Transactional or @Sql"));
        assertTrue(sql.getMessage().contains("@Transactional or @Sql"));
        assertTrue(classLevel.getMessage().contains("class-level @Transactional or @Sql"));
    }

    @Test
    void rejectsRedundantSpringBootTestBootstrapper() {
        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> JustTestClassValidationExtension.validateTestClass(RedundantBootTest.class));

        assertTrue(failure.getMessage().contains("must not combine @JustTest with @SpringBootTest"));
    }

    @Test
    void rejectsAnotherExplicitBootstrapper() {
        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> JustTestClassValidationExtension.validateTestClass(RedundantBootstrapper.class));

        assertTrue(failure.getMessage().contains("must not declare another @BootstrapWith"));
    }

    static class CaseSourceOnly implements JustTestLifecycle {
        @CaseSource
        void justCase() {
        }
    }

    static class MissingLifecycle {
        @CaseSource
        void justCase() {
        }
    }

    static class MixedTests implements JustTestLifecycle {
        @CaseSource
        void justCase() {
        }

        @Test
        void ordinaryTest() {
        }

        @RepeatedTest(2)
        void repeatedTest() {
        }
    }

    static class CompetingAnnotations implements JustTestLifecycle {
        @Test
        @CaseSource
        void mixed() {
        }
    }

    static class TransactionalCase implements JustTestLifecycle {
        @Transactional
        @CaseSource
        void justCase() {
        }
    }

    static class SqlCase implements JustTestLifecycle {
        @Sql
        @CaseSource
        void justCase() {
        }
    }

    @JustTest
    @Transactional
    static class TransactionalClass implements JustTestLifecycle {
        @CaseSource
        void justCase() {
        }
    }

    @JustTest
    @SpringBootTest
    static class RedundantBootTest {
        @CaseSource
        void justCase() {
        }
    }

    @JustTest
    @BootstrapWith(SpringBootTestContextBootstrapper.class)
    static class RedundantBootstrapper {
        @CaseSource
        void justCase() {
        }
    }
}

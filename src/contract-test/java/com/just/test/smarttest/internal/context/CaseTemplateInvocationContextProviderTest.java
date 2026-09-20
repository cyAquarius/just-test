package com.just.test.smarttest.internal.context;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.internal.context.discovery.abstractok.ConcreteFromSupport;
import com.just.test.smarttest.internal.context.discovery.classnamedbait.ClassNamedBaitFixture;
import com.just.test.smarttest.internal.context.discovery.conflict.FirstConflictFixture;
import com.just.test.smarttest.internal.context.discovery.customroot.CustomRootCases;
import com.just.test.smarttest.internal.context.discovery.legacy.LegacyClassNameCases;
import com.just.test.smarttest.internal.context.discovery.missing.MissingPackageYamlCases;
import com.just.test.smarttest.internal.context.discovery.missingcustom.MissingCustomRootCases;
import com.just.test.smarttest.internal.context.discovery.packageroot.PackageRootCases;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CaseTemplateInvocationContextProviderTest {

    @Test
    void discoversCasesUnderTestClassPackage() throws Exception {
        ExtensionContext context = mockContext(PackageRootCases.Cases.class, "caseMethod");

        CaseTemplateInvocationContextProvider provider = new CaseTemplateInvocationContextProvider();
        List<CaseContext> cases = provider.discoverCases(context);

        assertTrue(provider.supportsTestTemplate(context));
        assertEquals(1, cases.size());
        assertEquals("ok", cases.get(0).getCaseName());
        assertEquals(
                "com/just/test/smarttest/internal/context/discovery/packageroot/ok",
                cases.get(0).getCasePath());
        assertEquals(7, cases.get(0).getInt("value"));
    }

    @Test
    void discoversNestedFixtureCasesUnderEnclosingPackage() throws Exception {
        ExtensionContext context = mockContext(Fixture.class, "caseMethod");

        List<CaseContext> cases = new CaseTemplateInvocationContextProvider().discoverCases(context);

        assertEquals(1, cases.size());
        assertEquals("alpha", cases.get(0).getCaseName());
        assertEquals(7, cases.get(0).getInt("value"));
    }

    @Test
    void failsFastWhenPackageHasNoCaseDirectories() throws Exception {
        ExtensionContext context = mockContext(MissingPackageYamlCases.Cases.class, "caseMethod");
        String expectedRoot = "com/just/test/smarttest/internal/context/discovery/missing";

        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> new CaseTemplateInvocationContextProvider().discoverCases(context));

        assertTrue(failure.getMessage().contains("No YAML case directories found"), failure.getMessage());
        assertTrue(failure.getMessage().contains(expectedRoot), failure.getMessage());
        assertTrue(failure.getMessage().contains("no silent fallback to a class-named subdirectory"),
                failure.getMessage());
        assertTrue(failure.getMessage().contains(MissingPackageYamlCases.Cases.class.getName()),
                failure.getMessage());
    }

    @Test
    void doesNotFallBackToClassNamedSubdirectory() throws Exception {
        ExtensionContext context = mockContext(ClassNamedBaitFixture.class, "caseMethod");
        String packageRoot = "com/just/test/smarttest/internal/context/discovery/classnamedbait";
        String classNamedRoot = packageRoot + "/ClassNamedBaitFixture";

        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> new CaseTemplateInvocationContextProvider().discoverCases(context));

        assertTrue(failure.getMessage().contains("No YAML case directories found"), failure.getMessage());
        assertTrue(failure.getMessage().contains(packageRoot), failure.getMessage());
        assertFalse(failure.getMessage().contains(classNamedRoot), failure.getMessage());
    }

    @Test
    void discoversCasesUnderExplicitCaseSourceRootAndIgnoresPackageBait() throws Exception {
        ExtensionContext context = mockContext(CustomRootCases.Cases.class, "caseMethod");

        List<CaseContext> cases = new CaseTemplateInvocationContextProvider().discoverCases(context);

        assertEquals(1, cases.size());
        assertEquals("explicit-case", cases.get(0).getCaseName());
        assertEquals(
                "com/just/test/smarttest/internal/context/discovery/customroot/custom-root/explicit-case",
                cases.get(0).getCasePath());
        assertEquals(11, cases.get(0).getInt("value"));
    }

    @Test
    void failsFastWhenExplicitCaseSourceRootIsMissingAndDoesNotPickUpPackageYaml() throws Exception {
        ExtensionContext context = mockContext(MissingCustomRootCases.Cases.class, "caseMethod");
        String expectedRoot = "com/just/test/smarttest/internal/context/discovery/missingcustom/missing-custom-root";

        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> new CaseTemplateInvocationContextProvider().discoverCases(context));

        assertTrue(failure.getMessage().contains("No YAML case directories found"), failure.getMessage());
        assertTrue(failure.getMessage().contains(expectedRoot), failure.getMessage());
    }

    @Test
    void allowsLegacyClassNamedRootWhenCaseSourceNamesIt() throws Exception {
        ExtensionContext context = mockContext(LegacyClassNameCases.Cases.class, "caseMethod");

        List<CaseContext> cases = new CaseTemplateInvocationContextProvider().discoverCases(context);

        assertEquals(1, cases.size());
        assertEquals("legacy-case", cases.get(0).getCaseName());
        assertEquals(
                "com/just/test/smarttest/internal/context/discovery/legacy/LegacyClassName/legacy-case",
                cases.get(0).getCasePath());
        assertEquals(1, cases.get(0).getInt("value"));
    }

    @Test
    void failsFastWhenPackageContainsMultipleConcreteSmartTestClasses() throws Exception {
        ExtensionContext context = mockContext(FirstConflictFixture.class, "caseMethod");

        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> new CaseTemplateInvocationContextProvider().discoverCases(context));

        assertTrue(failure.getMessage().contains("multiple concrete @SmartTest classes"),
                failure.getMessage());
        assertTrue(failure.getMessage().contains(FirstConflictFixture.class.getName()),
                failure.getMessage());
        assertTrue(failure.getMessage().contains(
                "com.just.test.smarttest.internal.context.discovery.conflict.SecondConflictFixture"),
                failure.getMessage());
        assertTrue(failure.getMessage().contains(
                "com.just.test.smarttest.internal.context.discovery.conflict"),
                failure.getMessage());
    }

    @Test
    void allowsAbstractSupportBaseAlongsideOneConcreteSmartTest() throws Exception {
        ExtensionContext context = mockContext(ConcreteFromSupport.class, "caseMethod");

        List<CaseContext> cases = new CaseTemplateInvocationContextProvider().discoverCases(context);

        assertEquals(1, cases.size());
        assertEquals("from-support", cases.get(0).getCaseName());
        assertEquals(5, cases.get(0).getInt("value"));
    }

    @Test
    void rejectsCaseSourceWithoutSmartTest() throws Exception {
        ExtensionContext context = mock(ExtensionContext.class);
        Method method = InvalidFixture.class.getDeclaredMethod("caseMethod", CaseContext.class);
        doReturn(InvalidFixture.class).when(context).getRequiredTestClass();
        when(context.getTestMethod()).thenReturn(java.util.Optional.of(method));

        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> new CaseTemplateInvocationContextProvider().supportsTestTemplate(context));

        assertTrue(failure.getMessage().contains("uses @CaseSource without @SmartTest"));
    }

    private static ExtensionContext mockContext(Class<?> testClass, String methodName) throws Exception {
        ExtensionContext context = mock(ExtensionContext.class);
        Method method = testClass.getDeclaredMethod(methodName, CaseContext.class);
        doReturn(testClass).when(context).getRequiredTestClass();
        when(context.getRequiredTestMethod()).thenReturn(method);
        when(context.getTestMethod()).thenReturn(java.util.Optional.of(method));
        return context;
    }

    @SmartTest
    private static class Fixture {
        @CaseSource
        void caseMethod(CaseContext context) {
        }
    }

    private static class InvalidFixture {
        @CaseSource
        void caseMethod(CaseContext context) {
        }
    }
}

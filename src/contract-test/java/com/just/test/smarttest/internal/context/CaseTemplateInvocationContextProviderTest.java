package com.just.test.smarttest.internal.context;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.internal.context.packageroot.PackageRootCases;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CaseTemplateInvocationContextProviderTest {

    @Test
    void discoversCasesUnderClassNamedRoot() throws Exception {
        ExtensionContext context = mockContext(Fixture.class, "caseMethod");

        CaseTemplateInvocationContextProvider provider = new CaseTemplateInvocationContextProvider();
        List<CaseContext> cases = provider.discoverCases(context);

        assertTrue(provider.supportsTestTemplate(context));
        assertEquals(1, cases.size());
        assertEquals("alpha", cases.get(0).getCaseName());
        assertEquals(7, cases.get(0).getInt("value"));
    }

    @Test
    void failsFastWhenClassNamedDirectoryIsMissingAndDoesNotPickUpPackageYaml() throws Exception {
        ExtensionContext context = mockContext(PackageRootCases.MissingClassDirectory.class, "caseMethod");
        String expectedRoot = "com/just/test/smarttest/internal/context/packageroot/MissingClassDirectory";

        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> new CaseTemplateInvocationContextProvider().discoverCases(context));

        assertTrue(failure.getMessage().contains("No YAML case directories found"), failure.getMessage());
        assertTrue(failure.getMessage().contains(expectedRoot), failure.getMessage());
        assertTrue(failure.getMessage().contains(PackageRootCases.MissingClassDirectory.class.getName()),
                failure.getMessage());
    }

    @Test
    void discoversCasesUnderExplicitCaseSourceRoot() throws Exception {
        ExtensionContext context = mockContext(PackageRootCases.CustomRoot.class, "caseMethod");

        List<CaseContext> cases = new CaseTemplateInvocationContextProvider().discoverCases(context);

        assertEquals(1, cases.size());
        assertEquals("explicit-case", cases.get(0).getCaseName());
        assertEquals(
                "com/just/test/smarttest/internal/context/packageroot/custom-root/explicit-case",
                cases.get(0).getCasePath());
        assertEquals(11, cases.get(0).getInt("value"));
    }

    @Test
    void failsFastWhenExplicitCaseSourceRootIsMissingAndDoesNotPickUpPackageYaml() throws Exception {
        ExtensionContext context = mockContext(PackageRootCases.MissingCustomRoot.class, "caseMethod");
        String expectedRoot = "com/just/test/smarttest/internal/context/packageroot/missing-custom-root";

        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> new CaseTemplateInvocationContextProvider().discoverCases(context));

        assertTrue(failure.getMessage().contains("No YAML case directories found"), failure.getMessage());
        assertTrue(failure.getMessage().contains(expectedRoot), failure.getMessage());
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

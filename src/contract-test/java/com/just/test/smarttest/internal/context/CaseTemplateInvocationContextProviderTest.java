package com.just.test.smarttest.internal.context;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;
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
    void prefersTestClassDirectoryAndLoadsClasspathResources() throws Exception {
        ExtensionContext context = mock(ExtensionContext.class);
        Method method = Fixture.class.getDeclaredMethod("caseMethod", CaseContext.class);
        doReturn(Fixture.class).when(context).getRequiredTestClass();
        when(context.getRequiredTestMethod()).thenReturn(method);
        when(context.getTestMethod()).thenReturn(java.util.Optional.of(method));

        CaseTemplateInvocationContextProvider provider = new CaseTemplateInvocationContextProvider();
        List<CaseContext> cases = provider.discoverCases(context);

        assertTrue(provider.supportsTestTemplate(context));
        assertEquals(1, cases.size());
        assertEquals("alpha", cases.get(0).getCaseName());
        assertEquals(7, cases.get(0).getInt("value"));
    }

    @Test
    void fallsBackToPackageRootWhenClassNamedDirectoryIsMissing() throws Exception {
        ExtensionContext context = mock(ExtensionContext.class);
        Method method = PackageFallbackFixture.class.getDeclaredMethod("caseMethod", CaseContext.class);
        doReturn(PackageFallbackFixture.class).when(context).getRequiredTestClass();
        when(context.getRequiredTestMethod()).thenReturn(method);
        when(context.getTestMethod()).thenReturn(java.util.Optional.of(method));

        List<CaseContext> cases = new CaseTemplateInvocationContextProvider().discoverCases(context);

        assertEquals(2, cases.size());
        assertEquals("legacy-case", cases.get(0).getCaseName());
        assertEquals("sibling-case", cases.get(1).getCaseName());
        assertEquals("com/just/test/smarttest/internal/context/legacy-case", cases.get(0).getCasePath());
        assertEquals("com/just/test/smarttest/internal/context/sibling-case", cases.get(1).getCasePath());
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

    @SmartTest
    private static class Fixture {
        @CaseSource
        void caseMethod(CaseContext context) {
        }
    }

    @SmartTest
    private static class PackageFallbackFixture {
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

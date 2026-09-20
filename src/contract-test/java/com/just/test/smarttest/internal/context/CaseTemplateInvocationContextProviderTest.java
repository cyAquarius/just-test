package com.just.test.smarttest.internal.context;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.internal.context.caseroot.abstractparent.child.ConcreteChildCases;
import com.just.test.smarttest.internal.context.caseroot.abstractsame.AbstractSamePackageSupport;
import com.just.test.smarttest.internal.context.caseroot.abstractsame.ConcreteSamePackageCases;
import com.just.test.smarttest.internal.context.caseroot.classnamed.ClassNamedRootCases;
import com.just.test.smarttest.internal.context.caseroot.custom.CustomRootCases;
import com.just.test.smarttest.internal.context.caseroot.defaultdiscovery.DefaultDiscoveryCases;
import com.just.test.smarttest.internal.context.caseroot.inheritedmarker.child.UnannotatedChildCases;
import com.just.test.smarttest.internal.context.caseroot.missingcustom.MissingCustomRootCases;
import com.just.test.smarttest.internal.context.caseroot.multi.FirstConcreteCases;
import com.just.test.smarttest.internal.context.caseroot.multi.SecondConcreteCases;
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
    void discoversCasesUnderTestClassPackage() throws Exception {
        ExtensionContext context = mockContext(DefaultDiscoveryCases.class, "caseMethod");

        CaseTemplateInvocationContextProvider provider = new CaseTemplateInvocationContextProvider();
        List<CaseContext> cases = provider.discoverCases(context);

        assertTrue(provider.supportsTestTemplate(context));
        assertEquals(1, cases.size());
        assertEquals("package-case", cases.get(0).getCaseName());
        assertEquals(
                "com/just/test/smarttest/internal/context/caseroot/defaultdiscovery/package-case",
                cases.get(0).getCasePath());
        assertEquals(7, cases.get(0).getInt("value"));
    }

    @Test
    void failsFastWhenPackageHasNoYamlAndDoesNotProbeClassNamedDirectory() throws Exception {
        ExtensionContext context = mockContext(ClassNamedRootCases.class, "caseMethod");
        String expectedRoot = "com/just/test/smarttest/internal/context/caseroot/classnamed";

        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> new CaseTemplateInvocationContextProvider().discoverCases(context));

        assertTrue(failure.getMessage().contains("No YAML case directories found"), failure.getMessage());
        assertTrue(failure.getMessage().contains(expectedRoot), failure.getMessage());
        assertTrue(failure.getMessage().contains("siblings under the test class package"),
                failure.getMessage());
        assertTrue(failure.getMessage().contains("does not probe {package}/{SimpleClassName}/"),
                failure.getMessage());
        assertTrue(failure.getMessage().contains(ClassNamedRootCases.class.getName()),
                failure.getMessage());
    }

    @Test
    void discoversCasesUnderExplicitCaseSourceRoot() throws Exception {
        ExtensionContext context = mockContext(CustomRootCases.class, "caseMethod");

        List<CaseContext> cases = new CaseTemplateInvocationContextProvider().discoverCases(context);

        assertEquals(1, cases.size());
        assertEquals("explicit-case", cases.get(0).getCaseName());
        assertEquals(
                "com/just/test/smarttest/internal/context/caseroot/custom/custom-root/explicit-case",
                cases.get(0).getCasePath());
        assertEquals(11, cases.get(0).getInt("value"));
    }

    @Test
    void failsFastWhenExplicitCaseSourceRootIsMissingAndDoesNotPickUpPackageYaml() throws Exception {
        ExtensionContext context = mockContext(MissingCustomRootCases.class, "caseMethod");
        String expectedRoot = "com/just/test/smarttest/internal/context/caseroot/missingcustom/missing-custom-root";

        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> new CaseTemplateInvocationContextProvider().discoverCases(context));

        assertTrue(failure.getMessage().contains("No YAML case directories found"), failure.getMessage());
        assertTrue(failure.getMessage().contains(expectedRoot), failure.getMessage());
        assertTrue(failure.getMessage().contains("siblings under the test class package"),
                failure.getMessage());
    }

    @Test
    void failsFastWhenPackageHasMultipleConcreteSmartTestClasses() throws Exception {
        ExtensionContext context = mockContext(FirstConcreteCases.class, "caseMethod");

        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> new CaseTemplateInvocationContextProvider().discoverCases(context));

        assertTrue(failure.getMessage().contains("multiple concrete @SmartTest classes"),
                failure.getMessage());
        assertTrue(failure.getMessage().contains(FirstConcreteCases.class.getName()),
                failure.getMessage());
        assertTrue(failure.getMessage().contains(SecondConcreteCases.class.getName()),
                failure.getMessage());
        assertTrue(failure.getMessage().contains("siblings under the test class package"),
                failure.getMessage());
    }

    @Test
    void failsFastWhenAbstractClassInTheSamePackageHasSmartTest() throws Exception {
        ExtensionContext context = mockContext(ConcreteSamePackageCases.class, "caseMethod");

        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> new CaseTemplateInvocationContextProvider().discoverCases(context));

        assertTrue(failure.getMessage().contains("abstract @SmartTest class"), failure.getMessage());
        assertTrue(failure.getMessage().contains(AbstractSamePackageSupport.class.getName()),
                failure.getMessage());
        assertTrue(failure.getMessage().contains("must not carry @SmartTest"), failure.getMessage());
        assertTrue(failure.getMessage().contains("concrete method-package test class"),
                failure.getMessage());
    }

    @Test
    void discoversCasesWhenUnannotatedSupportLivesInAParentPackage() throws Exception {
        ExtensionContext context = mockContext(ConcreteChildCases.class, "caseMethod");

        CaseTemplateInvocationContextProvider provider = new CaseTemplateInvocationContextProvider();
        List<CaseContext> cases = provider.discoverCases(context);

        assertTrue(provider.supportsTestTemplate(context));
        assertEquals(1, cases.size());
        assertEquals("ok", cases.get(0).getCaseName());
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
        assertTrue(failure.getMessage().contains("concrete test class"), failure.getMessage());
        assertTrue(failure.getMessage().contains("must not carry @SmartTest"), failure.getMessage());
    }

    @Test
    void rejectsConcreteClassThatOnlyInheritsSmartTestFromAbstractSupport() throws Exception {
        ExtensionContext context = mockContext(UnannotatedChildCases.class, "caseMethod");

        ExtensionConfigurationException failure = assertThrows(ExtensionConfigurationException.class,
                () -> new CaseTemplateInvocationContextProvider().supportsTestTemplate(context));

        assertTrue(failure.getMessage().contains("uses @CaseSource without @SmartTest"),
                failure.getMessage());
        assertTrue(failure.getMessage().contains(UnannotatedChildCases.class.getName()),
                failure.getMessage());
        assertTrue(failure.getMessage().contains("concrete test class"), failure.getMessage());
        assertTrue(failure.getMessage().contains("must not carry @SmartTest"), failure.getMessage());
    }

    private static ExtensionContext mockContext(Class<?> testClass, String methodName) throws Exception {
        ExtensionContext context = mock(ExtensionContext.class);
        Method method = testClass.getDeclaredMethod(methodName, CaseContext.class);
        doReturn(testClass).when(context).getRequiredTestClass();
        when(context.getRequiredTestMethod()).thenReturn(method);
        when(context.getTestMethod()).thenReturn(java.util.Optional.of(method));
        return context;
    }

    private static class InvalidFixture {
        @CaseSource
        void caseMethod(CaseContext context) {
        }
    }
}

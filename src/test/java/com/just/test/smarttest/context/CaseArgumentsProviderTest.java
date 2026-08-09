package com.just.test.smarttest.context;

import com.just.test.smarttest.annotation.CaseSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CaseArgumentsProviderTest {

    @Test
    void prefersTestClassDirectoryAndLoadsClasspathResources() throws Exception {
        ExtensionContext context = mock(ExtensionContext.class);
        Method method = Fixture.class.getDeclaredMethod("caseMethod", CaseContext.class);
        doReturn(Fixture.class).when(context).getRequiredTestClass();
        when(context.getRequiredTestMethod()).thenReturn(method);

        List<? extends Arguments> arguments = new CaseArgumentsProvider()
                .provideArguments(context)
                .collect(Collectors.toList());

        assertEquals(1, arguments.size());
        CaseContext caseContext = (CaseContext) arguments.get(0).get()[0];
        assertEquals("alpha", caseContext.getCaseName());
        assertEquals(7, caseContext.getInt("value"));
    }

    private static class Fixture {
        @CaseSource
        void caseMethod(CaseContext context) {
        }
    }
}

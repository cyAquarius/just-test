package com.just.test.smarttest.lifecycle;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationContextDiagnosticsTest {

    @Test
    void warnsOnceAfterASecondContextAppearsAndOncePerFailingClass() {
        ApplicationContextDiagnostics diagnostics = new ApplicationContextDiagnostics();
        GenericApplicationContext first = new GenericApplicationContext();
        GenericApplicationContext second = new GenericApplicationContext();
        try {
            diagnostics.observe(first);
            assertFalse(diagnostics.hasMultipleContexts());
            assertFalse(diagnostics.markRiskWarning());

            diagnostics.observe(second);
            assertTrue(diagnostics.hasMultipleContexts());
            assertTrue(diagnostics.markRiskWarning());
            assertFalse(diagnostics.markRiskWarning());
            assertTrue(diagnostics.markFailureWarningFor(FirstTest.class));
            assertFalse(diagnostics.markFailureWarningFor(FirstTest.class));
            assertTrue(diagnostics.markFailureWarningFor(SecondTest.class));
        } finally {
            first.close();
            second.close();
        }
    }

    private static class FirstTest {
    }

    private static class SecondTest {
    }
}

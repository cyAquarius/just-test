package com.just.test.smarttest.internal.context.discovery.legacy;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;

/**
 * Escape hatch: {@code @CaseSource} value is the simple class name of the old layout.
 */
public final class LegacyClassNameCases {

    private LegacyClassNameCases() {
    }

    @SmartTest
    public static class Cases {
        @CaseSource("LegacyClassName")
        void caseMethod(CaseContext context) {
        }
    }
}

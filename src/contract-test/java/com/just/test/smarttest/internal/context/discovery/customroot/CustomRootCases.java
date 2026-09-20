package com.just.test.smarttest.internal.context.discovery.customroot;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;

/**
 * Explicit {@code @CaseSource} root. Package-level bait YAML must not be picked up.
 */
public final class CustomRootCases {

    private CustomRootCases() {
    }

    @SmartTest
    public static class Cases {
        @CaseSource("custom-root")
        void caseMethod(CaseContext context) {
        }
    }
}

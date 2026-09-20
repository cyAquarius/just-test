package com.just.test.smarttest.internal.context.discovery.missingcustom;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;

/**
 * Missing explicit root. Package-level bait YAML must not be used as a fallback.
 */
public final class MissingCustomRootCases {

    private MissingCustomRootCases() {
    }

    @SmartTest
    public static class Cases {
        @CaseSource("missing-custom-root")
        void caseMethod(CaseContext context) {
        }
    }
}

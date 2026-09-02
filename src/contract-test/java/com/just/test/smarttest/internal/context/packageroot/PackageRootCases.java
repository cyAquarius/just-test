package com.just.test.smarttest.internal.context.packageroot;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;

/**
 * Holder for package-level YAML fallback discovery. Not a Surefire test class.
 */
public final class PackageRootCases {

    private PackageRootCases() {
    }

    @SmartTest
    public static class MissingClassDirectory {
        @CaseSource
        void caseMethod(CaseContext context) {
        }
    }
}

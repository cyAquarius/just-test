package com.just.test.smarttest.internal.context.packageroot;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;

/**
 * Holder for case-root discovery fixtures. Not a Surefire test class.
 * Package-level YAML under this package is bait: it must not be discovered
 * unless an explicit {@code @CaseSource} root names it.
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

    @SmartTest
    public static class CustomRoot {
        @CaseSource("custom-root")
        void caseMethod(CaseContext context) {
        }
    }

    @SmartTest
    public static class MissingCustomRoot {
        @CaseSource("missing-custom-root")
        void caseMethod(CaseContext context) {
        }
    }
}

package com.just.test.smarttest.internal.context.discovery.packageroot;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;

/**
 * Default discovery fixture: case dirs sit in this package, as siblings of the test class.
 */
public final class PackageRootCases {

    private PackageRootCases() {
    }

    @SmartTest
    public static class Cases {
        @CaseSource
        void caseMethod(CaseContext context) {
        }
    }
}

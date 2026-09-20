package com.just.test.smarttest.internal.context.discovery.missing;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;

/** Package has no sibling case directories. */
public final class MissingPackageYamlCases {

    private MissingPackageYamlCases() {
    }

    @SmartTest
    public static class Cases {
        @CaseSource
        void caseMethod(CaseContext context) {
        }
    }
}

package com.just.test.smarttest.internal.context.caseroot.missingcustom;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;

@SmartTest
public class MissingCustomRootCases {
    @CaseSource("missing-custom-root")
    void caseMethod(CaseContext context) {
    }
}

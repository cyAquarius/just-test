package com.just.test.internal.context.caseroot.missingcustom;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;

@JustTest
public class MissingCustomRootCases {
    @CaseSource("missing-custom-root")
    void caseMethod(CaseContext context) {
    }
}

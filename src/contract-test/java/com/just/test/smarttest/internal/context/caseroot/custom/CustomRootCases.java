package com.just.test.smarttest.internal.context.caseroot.custom;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;

@SmartTest
public class CustomRootCases {
    @CaseSource("custom-root")
    void caseMethod(CaseContext context) {
    }
}

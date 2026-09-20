package com.just.test.internal.context.caseroot.custom;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;

@JustTest
public class CustomRootCases {
    @CaseSource("custom-root")
    void caseMethod(CaseContext context) {
    }
}

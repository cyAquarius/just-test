package com.just.test.internal.context.caseroot.multi;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;

@JustTest
public class FirstConcreteCases {
    @CaseSource
    void caseMethod(CaseContext context) {
    }
}

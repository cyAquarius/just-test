package com.just.test.smarttest.internal.context.caseroot.multi;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;

@SmartTest
public class FirstConcreteCases {
    @CaseSource
    void caseMethod(CaseContext context) {
    }
}

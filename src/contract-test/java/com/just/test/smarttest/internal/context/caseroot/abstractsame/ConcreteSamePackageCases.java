package com.just.test.smarttest.internal.context.caseroot.abstractsame;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;

@SmartTest
public class ConcreteSamePackageCases extends AbstractSamePackageSupport {
    @CaseSource
    void caseMethod(CaseContext context) {
    }
}

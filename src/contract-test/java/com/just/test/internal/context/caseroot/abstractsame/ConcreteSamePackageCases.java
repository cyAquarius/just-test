package com.just.test.internal.context.caseroot.abstractsame;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;

@JustTest
public class ConcreteSamePackageCases extends AbstractSamePackageSupport {
    @CaseSource
    void caseMethod(CaseContext context) {
    }
}

package com.just.test.internal.context.caseroot.abstractparent.child;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.internal.context.caseroot.abstractparent.AbstractParentSupport;

@JustTest
public class ConcreteChildCases extends AbstractParentSupport {
    @CaseSource
    void caseMethod(CaseContext context) {
    }
}

package com.just.test.smarttest.internal.context.caseroot.abstractparent.child;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.internal.context.caseroot.abstractparent.AbstractParentSupport;

@SmartTest
public class ConcreteChildCases extends AbstractParentSupport {
    @CaseSource
    void caseMethod(CaseContext context) {
    }
}

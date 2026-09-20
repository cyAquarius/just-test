package com.just.test.smarttest.internal.context.caseroot.inheritedmarker.child;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.internal.context.caseroot.inheritedmarker.AnnotatedAbstractSupport;

/**
 * Invalid fixture: concrete class inherits {@code @SmartTest} from Support but does not declare it.
 */
public class UnannotatedChildCases extends AnnotatedAbstractSupport {
    @CaseSource
    void caseMethod(CaseContext context) {
    }
}

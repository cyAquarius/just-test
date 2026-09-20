package com.just.test.internal.context.caseroot.inheritedmarker.child;

import com.just.test.annotation.CaseSource;
import com.just.test.context.CaseContext;
import com.just.test.internal.context.caseroot.inheritedmarker.AnnotatedAbstractSupport;

/**
 * Invalid fixture: concrete class inherits {@code @JustTest} from Support but does not declare it.
 */
public class UnannotatedChildCases extends AnnotatedAbstractSupport {
    @CaseSource
    void caseMethod(CaseContext context) {
    }
}

package com.just.test.smarttest.internal.context.discovery.classnamedbait;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;

/**
 * YAML lives only under the old class-named layout. Default discovery must not pick it up.
 */
@SmartTest
public class ClassNamedBaitFixture {
    @CaseSource
    public void caseMethod(CaseContext context) {
    }
}

package com.just.test.smarttest.internal.context.discovery.conflict;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;

@SmartTest
public class FirstConflictFixture {
    @CaseSource
    public void caseMethod(CaseContext context) {
    }
}

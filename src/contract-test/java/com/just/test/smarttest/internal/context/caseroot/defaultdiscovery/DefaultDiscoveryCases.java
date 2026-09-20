package com.just.test.smarttest.internal.context.caseroot.defaultdiscovery;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;

@SmartTest
public class DefaultDiscoveryCases {
    @CaseSource
    void caseMethod(CaseContext context) {
    }
}

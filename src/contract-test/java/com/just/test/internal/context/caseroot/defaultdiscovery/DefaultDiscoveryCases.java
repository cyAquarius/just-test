package com.just.test.internal.context.caseroot.defaultdiscovery;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;

@JustTest
public class DefaultDiscoveryCases {
    @CaseSource
    void caseMethod(CaseContext context) {
    }
}

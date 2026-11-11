package com.just.test.mock.interceptfinal;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;

/**
 * 显式拦截 final 类型。类名不以 Test 结尾，由 {@code InterceptFailFastTest} 启动。
 */
@JustTest
public class FinalClockCases implements JustTestLifecycle {

    @CaseSource
    void shouldFailAtContextStart(CaseContext context) {
        context.setResult("should-not-run");
    }
}

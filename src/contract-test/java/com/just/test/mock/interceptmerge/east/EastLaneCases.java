package com.just.test.mock.interceptmerge.east;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import com.just.test.mock.interceptmerge.fixtures.EastLane;

/**
 * 实现类声明唯一，接口声明在别的测试类里。类名不以 Test 结尾，
 * 由 {@code InterceptMergeFailFastTest} 启动。
 */
@JustTest
public class EastLaneCases implements JustTestLifecycle {

    @JustMock
    private EastLane eastLane;

    @CaseSource
    void shouldFailAtContextStart(CaseContext context) {
        context.setResult("should-not-run");
    }
}

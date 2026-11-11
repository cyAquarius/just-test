package com.just.test.mock.interceptoutside;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import com.just.test.mock.interceptmiss.JustTestApplication;
import com.just.test.mock.interceptmiss.fixtures.OrphanClock;
import org.springframework.test.context.ContextConfiguration;

/**
 * 使用同一个启动类，但不在该启动类包下，扫描看不到这里的 {@code @JustMock}。
 * 类名不以 Test 结尾，由 {@code InterceptFailFastTest} 启动。
 */
@JustTest
@ContextConfiguration(classes = JustTestApplication.class)
public class OutsideMissCases implements JustTestLifecycle {

    @JustMock
    private OrphanClock orphanClock;

    @CaseSource
    void shouldFailBeforeTheCase(CaseContext context) {
        context.setResult("should-not-run");
    }
}

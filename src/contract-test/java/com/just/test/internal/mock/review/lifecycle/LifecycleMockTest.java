package com.just.test.internal.mock.review.lifecycle;

import com.just.test.internal.mock.review.lifecycle.mock.LifecycleMockCases;
import com.just.test.internal.mock.review.lifecycle.plain.LifecyclePlainCases;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestContextManager;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

class LifecycleMockTest {

    @Test
    void caseScopeDoesNotInitializeOrCloseHiddenSingletons() {
        execute(LifecycleMockCases.class, 2);
        ApplicationContext context = contextFor(LifecycleMockCases.class);
        context.getBean(LifecycleMockConfig.Counters.class).assertRealBeansOpen();

        execute(LifecyclePlainCases.class, 1);
        assertSame(context, contextFor(LifecyclePlainCases.class));
    }

    private void execute(Class<?> cases, long count) {
        EngineExecutionResults results = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(cases))
                .configurationParameter("junit.jupiter.execution.parallel.enabled", "false")
                .execute();
        results.testEvents().assertStatistics(stats -> stats.started(count).succeeded(count));
        results.containerEvents().assertStatistics(stats -> stats.failed(0));
    }

    private ApplicationContext contextFor(Class<?> cases) {
        return new TestContextManager(cases).getTestContext().getApplicationContext();
    }
}

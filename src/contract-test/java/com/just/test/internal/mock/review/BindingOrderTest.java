package com.just.test.internal.mock.review;

import com.just.test.internal.mock.review.aliased.explicit.AliasCases;
import com.just.test.internal.mock.review.aliased.plain.AliasPlainCases;
import com.just.test.internal.mock.review.excluded.mock.ExcludedMockCases;
import com.just.test.internal.mock.review.excluded.plain.ExcludedPlainCases;
import com.just.test.internal.mock.review.generic.order.OrderCases;
import com.just.test.internal.mock.review.generic.user.UserCases;
import com.just.test.internal.mock.review.named.explicit.NamedCases;
import com.just.test.internal.mock.review.named.plain.PlainCases;
import com.just.test.internal.mock.review.primary.mock.PrimaryMockCases;
import com.just.test.internal.mock.review.primary.plain.PrimaryPlainCases;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.cache.DefaultCacheAwareContextLoaderDelegate;
import org.springframework.test.annotation.DirtiesContext.HierarchyMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

@Isolated
class BindingOrderTest extends DefaultCacheAwareContextLoaderDelegate {

    @Test
    void genericSpiesBindTheCorrectRepository() {
        bothOrders(UserCases.class, OrderCases.class, false);
    }

    @Test
    void explicitNameSharesThreadScopedSource() {
        bothOrders(NamedCases.class, PlainCases.class, true);
    }

    @Test
    void explicitAliasSharesThreadScopedSource() {
        bothOrders(AliasCases.class, AliasPlainCases.class, true);
    }

    @Test
    void excludedTestConfigurationDoesNotHideReplacementFromCacheKey() {
        bothOrders(ExcludedMockCases.class, ExcludedPlainCases.class, false);
    }

    @Test
    void primaryComponentDoesNotHideReplacementFromCacheKey() {
        bothOrders(PrimaryMockCases.class, PrimaryPlainCases.class, false);
    }

    private void bothOrders(Class<?> first, Class<?> second, boolean shared) {
        runInOrder(first, second, shared);
        runInOrder(second, first, shared);
    }

    private void runInOrder(Class<?> first, Class<?> second, boolean shared) {
        getContextCache().clear();
        TestContextManager firstManager = new TestContextManager(first);
        TestContextManager secondManager = new TestContextManager(second);
        try {
            execute(first);
            execute(second);
            assertEquals(shared, firstManager.getTestContext().getApplicationContext()
                    == secondManager.getTestContext().getApplicationContext());
        } finally {
            firstManager.getTestContext().markApplicationContextDirty(HierarchyMode.EXHAUSTIVE);
            secondManager.getTestContext().markApplicationContextDirty(HierarchyMode.EXHAUSTIVE);
        }
    }

    private void execute(Class<?> testClass) {
        EngineExecutionResults results = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(testClass))
                .configurationParameter("junit.jupiter.execution.parallel.enabled", "false")
                .execute();
        results.testEvents().assertStatistics(stats -> stats.started(1).succeeded(1));
        results.containerEvents().assertStatistics(stats -> stats.failed(0));
    }
}

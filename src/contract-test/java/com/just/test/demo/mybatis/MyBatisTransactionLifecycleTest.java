package com.just.test.demo.mybatis;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.testkit.engine.EventConditions.event;
import static org.junit.platform.testkit.engine.EventConditions.finishedWithFailure;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.message;

class MyBatisTransactionLifecycleTest {

    @Test
    void boundResourcesFailTheirCaseAndAreReleasedBeforeTheNextCase() {
        EngineExecutionResults results = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(MyBatisTransactionLifecycleCases.class))
                .configurationParameter("junit.jupiter.execution.parallel.enabled", "false")
                .execute();
        results.testEvents().assertStatistics(stats -> stats.started(2).succeeded(1).failed(1));
        results.testEvents().failed().assertEventsMatchExactly(
                event(finishedWithFailure(message(text -> text.contains("Unfinished Spring transaction leaked")))));
        results.containerEvents().assertStatistics(stats -> stats.failed(0));
    }
}

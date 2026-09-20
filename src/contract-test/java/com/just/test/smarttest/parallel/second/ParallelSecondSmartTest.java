package com.just.test.smarttest.parallel.second;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.lifecycle.SmartTestLifecycle;
import com.just.test.smarttest.parallel.ParallelClassConfiguration;
import com.just.test.smarttest.parallel.ParallelClassCoordinator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.test.context.ContextConfiguration;

@SmartTest
@Execution(ExecutionMode.CONCURRENT)
@ContextConfiguration(classes = ParallelClassConfiguration.class)
class ParallelSecondSmartTest implements SmartTestLifecycle {

    @CaseSource
    void overlapsAnotherSmartTestClass(CaseContext context) throws Exception {
        ParallelClassCoordinator.overlap("second", context.getString("marker"));
    }

    @AfterAll
    static void verifiesClassLevelParallelism() {
        ParallelClassCoordinator.assertClassesOverlapped();
    }
}

package com.just.test.parallel.second;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import com.just.test.parallel.ParallelClassConfiguration;
import com.just.test.parallel.ParallelClassCoordinator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.test.context.ContextConfiguration;

@JustTest
@Execution(ExecutionMode.CONCURRENT)
@ContextConfiguration(classes = ParallelClassConfiguration.class)
class ParallelSecondJustTest implements JustTestLifecycle {

    @CaseSource
    void overlapsAnotherJustTestClass(CaseContext context) throws Exception {
        ParallelClassCoordinator.overlap("second", context.getString("marker"));
    }

    @AfterAll
    static void verifiesClassLevelParallelism() {
        ParallelClassCoordinator.assertClassesOverlapped();
    }
}

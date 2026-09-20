package com.just.test.lifecycle.beforecase;

import com.just.test.annotation.BeforeCase;
import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@JustTest
@ContextConfiguration(classes = BeforeCaseContractJustTest.EmptyConfiguration.class)
class BeforeCaseContractJustTest extends BeforeCaseContractParent {

    @BeforeCase("matching")
    void childSetupForMatchingCase() {
        order.add("child-beforeCase");
    }

    @CaseSource
    void invokesMatchingBeforeCaseBeforeBeforeExecute(CaseContext context) {
        if ("matching".equals(context.getCaseName())) {
            assertEquals(Arrays.asList("child-beforeCase", "parent-beforeCase", "beforeExecute"),
                    order);
            return;
        }
        assertEquals(Arrays.asList("beforeExecute"), order);
    }

    @Configuration
    static class EmptyConfiguration {
    }
}

abstract class BeforeCaseContractParent implements JustTestLifecycle {

    final List<String> order = new ArrayList<>();

    @BeforeCase("matching")
    void parentSetupForMatchingCase() {
        order.add("parent-beforeCase");
    }

    @Override
    public void beforeExecute(CaseContext context) {
        order.add("beforeExecute");
    }
}

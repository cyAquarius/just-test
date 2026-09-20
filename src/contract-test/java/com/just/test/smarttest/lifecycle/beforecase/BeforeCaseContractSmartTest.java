package com.just.test.smarttest.lifecycle.beforecase;

import com.just.test.smarttest.annotation.BeforeCase;
import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.lifecycle.SmartTestLifecycle;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SmartTest
@ContextConfiguration(classes = BeforeCaseContractSmartTest.EmptyConfiguration.class)
class BeforeCaseContractSmartTest extends BeforeCaseContractParent {

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

abstract class BeforeCaseContractParent implements SmartTestLifecycle {

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

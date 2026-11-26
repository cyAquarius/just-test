package com.just.test.lifecycle.beforecase.override;

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
@ContextConfiguration(classes = BeforeCaseOverrideContractJustTest.EmptyConfiguration.class)
class BeforeCaseOverrideContractJustTest extends BeforeCaseOverrideParent {

    @BeforeCase("matching")
    void aaaChild() {
        order.add("aaa-child");
    }

    @Override
    @BeforeCase("matching")
    void overriddenSetup() {
        super.overriddenSetup();
        order.add("child-body");
    }

    @Override
    @BeforeCase("different")
    void renamedSetup() {
        order.add("different-case");
    }

    @CaseSource
    void invokesOverriddenBeforeCaseOnce(CaseContext context) {
        if ("different".equals(context.getCaseName())) {
            assertEquals(Arrays.asList("different-case", "beforeExecute"), order);
            return;
        }
        assertEquals("matching", context.getCaseName());
        // 父类未覆写的方法仍先执行。覆写留在子类的方法名位置，并且只进入一次。
        assertEquals(Arrays.asList(
                "kept-parent",
                "aaa-child",
                "parent-body",
                "child-body",
                "beforeExecute"), order);
    }

    @Configuration
    static class EmptyConfiguration {
    }
}

abstract class BeforeCaseOverrideParent implements JustTestLifecycle {

    final List<String> order = new ArrayList<>();

    @BeforeCase("matching")
    void keptParent() {
        order.add("kept-parent");
    }

    @BeforeCase("matching")
    void overriddenSetup() {
        order.add("parent-body");
    }

    @BeforeCase("matching")
    void renamedSetup() {
        order.add("old-case");
    }

    @Override
    public void beforeExecute(CaseContext context) {
        order.add("beforeExecute");
    }
}

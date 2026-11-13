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
    void zetaChildSetup() {
        order.add("zeta-child");
    }

    @BeforeCase("matching")
    void alphaChildSetup() {
        order.add("alpha-child");
    }

    @CaseSource
    void invokesMatchingBeforeCaseBeforeBeforeExecute(CaseContext context) {
        if ("matching".equals(context.getCaseName())) {
            // 声明顺序是 zeta 再 alpha。合同按方法名排序，并且父类先于子类。
            assertEquals(Arrays.asList(
                    "alpha-parent", "zeta-parent",
                    "alpha-child", "zeta-child",
                    "beforeExecute"), order);
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
    void zetaParentSetup() {
        order.add("zeta-parent");
    }

    @BeforeCase("matching")
    void alphaParentSetup() {
        order.add("alpha-parent");
    }

    @Override
    public void beforeExecute(CaseContext context) {
        order.add("beforeExecute");
    }
}

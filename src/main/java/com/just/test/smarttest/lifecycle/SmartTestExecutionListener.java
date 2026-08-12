package com.just.test.smarttest.lifecycle;

import org.springframework.core.Ordered;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

/**
 * Smart Test 生命周期管理器。
 *
 * <p>职责：beforeTestClass 阶段初始化 Schema。
 * 数据驱动方法的 prepare / verify / clean 全部由 {@link SmartTestExtension} 负责。</p>
 */
public class SmartTestExecutionListener implements TestExecutionListener, Ordered {


    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void beforeTestClass(TestContext testContext) {
        // Schema 必须在绑定活动 case 后初始化，避免 beforeTestClass 工作线程与 case 线程不一致。
    }
}

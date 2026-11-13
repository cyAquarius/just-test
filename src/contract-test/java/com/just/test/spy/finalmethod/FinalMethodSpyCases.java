package com.just.test.spy.finalmethod;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustSpy;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;

/**
 * Surefire 不直接执行本类。{@code FinalMethodSpyFailFastTest} 在 Boot 2 的
 * subclass MockMaker 进程里断言启动失败，在 Boot 3 上断言同一 {@code @JustSpy} 路径能跑完。
 */
@JustTest
@ContextConfiguration(classes = FinalMethodSpyCases.TestConfiguration.class)
public class FinalMethodSpyCases implements JustTestLifecycle {

    public static final AtomicReference<String> SEEN = new AtomicReference<String>();

    @JustSpy
    private RateBean rateBean;

    @CaseSource
    void stubsFinalMethodAndLeavesOpenReal(CaseContext context) {
        doReturn("stub-label").when(rateBean).label();

        assertEquals("stub-label", rateBean.label());
        assertEquals("real-open", rateBean.open());
        SEEN.set(rateBean.label());
        context.setResult(rateBean.label());
    }

    public static class RateParent {
        protected final String fromParent() {
            return "parent";
        }
    }

    public static class RateBean extends RateParent {
        public final String label() {
            return "real-label";
        }

        protected final String detail() {
            return "real-detail";
        }

        public String open() {
            return "real-open";
        }
    }

    @Configuration
    public static class TestConfiguration {
        @Bean
        RateBean rateBean() {
            return new RateBean();
        }
    }
}

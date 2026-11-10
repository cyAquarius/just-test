package com.just.test.spy.concrete;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustSpy;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;

@JustTest
@ContextConfiguration(classes = JustSpyConcreteJustTest.TestConfiguration.class)
class JustSpyConcreteJustTest implements JustTestLifecycle {

    @Autowired
    private QuoteConsumer quoteConsumer;

    @JustSpy
    private QuoteBean quoteBean;

    @CaseSource
    void stubsOneMethodAndLeavesTheRestReal(CaseContext context) {
        doReturn("stub-quote").when(quoteBean).quote();

        assertEquals("stub-quote", quoteConsumer.quote());
        assertEquals("real-tax", quoteConsumer.tax());
        assertEquals("real-quote+tax", quoteConsumer.bundle());
    }

    public static class QuoteBean {
        public String quote() {
            return "real-quote";
        }

        public String tax() {
            return "real-tax";
        }

        public String bundle() {
            return quote() + "+tax";
        }
    }

    static class QuoteConsumer {
        private final QuoteBean quoteBean;

        QuoteConsumer(QuoteBean quoteBean) {
            this.quoteBean = quoteBean;
        }

        String quote() {
            return quoteBean.quote();
        }

        String tax() {
            return quoteBean.tax();
        }

        String bundle() {
            return quoteBean.bundle();
        }
    }

    @Configuration
    static class TestConfiguration {
        @Bean
        QuoteBean quoteBean() {
            return new QuoteBean();
        }

        @Bean
        QuoteConsumer quoteConsumer(QuoteBean quoteBean) {
            return new QuoteConsumer(quoteBean);
        }
    }
}

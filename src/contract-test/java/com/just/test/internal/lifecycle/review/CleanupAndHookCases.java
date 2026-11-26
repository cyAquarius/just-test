package com.just.test.internal.lifecycle.review;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

@JustTest
@ContextConfiguration(classes = CleanupAndHookCases.Config.class)
public class CleanupAndHookCases implements JustTestLifecycle {

    @Autowired
    @Qualifier("justTestTransactionManager")
    private PlatformTransactionManager transactionManager;

    @CaseSource("cases")
    void runsCase(CaseContext context) {
        String name = context.getCaseName();
        if (name.startsWith("verify-")) {
            throw new IllegalArgumentException("body-failure");
        }
        if ("abort-with-leak".equals(name)) {
            transactionManager.getTransaction(new DefaultTransactionDefinition());
        }
        if (name.startsWith("abort-")) {
            assumeTrue(false, "assumption failed");
        }
    }

    @Override
    public boolean verifyException(CaseContext context) {
        return verify(context, "verify-exception");
    }

    @Override
    public boolean verifyResult(CaseContext context) {
        return verify(context, "verify-result");
    }

    @Override
    public boolean verifyDatabase(CaseContext context, JdbcTemplate jdbc) {
        return verify(context, "verify-database");
    }

    private boolean verify(CaseContext context, String name) {
        if (name.equals(context.getCaseName())) {
            throw new IllegalStateException(name);
        }
        return true;
    }

    @Configuration
    static class Config {
        @Lazy
        @Bean
        DataSource mustStayLazy() {
            throw new AssertionError("transaction cleanup initialized a lazy DataSource");
        }
    }
}

package com.just.test.internal.loader.casestages;

import com.just.test.annotation.BeforeCase;
import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import org.junit.jupiter.api.Assumptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

// Invoked by CaseStageContractTest so expected failures can be asserted.
@JustTest
@ContextConfiguration(classes = CaseStageCases.EmptyConfiguration.class)
class CaseStageCases implements JustTestLifecycle {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @CaseSource
    void run(CaseContext context) {
        switch (context.getCaseName()) {
            case "case with spaces +":
            case "中文用例":
                assertEquals("loaded", context.getString("marker"));
                assertEquals("loaded", jdbcTemplate.queryForObject(
                        "SELECT marker FROM parallel_case_record WHERE id = 1", String.class));
                jdbcTemplate.update("UPDATE parallel_case_record SET marker = 'verified' WHERE id = 1");
                context.setResult("verified");
                break;
            case "empty-lists":
            case "empty-request":
                assertEquals(0, context.getParams().size());
                break;
            case "body-abort":
                Assumptions.assumeTrue(false, "aborted in test body");
                break;
            case "broken-request":
            case "no-stages":
            case "before-abort":
            case "before-assertion":
            case "prepare-mapping":
            case "prepare-scalar":
            case "prepare-row":
                fail("This case must stop before the test body");
                break;
            default:
                // Malformed expect files are checked after the body returns.
        }
    }

    @BeforeCase("before-abort")
    void abortBeforeCase() {
        Assumptions.assumeTrue(false, "aborted in before case");
    }

    @BeforeCase("before-assertion")
    void assertBeforeCase() {
        throw new AssertionError("assertion in before case");
    }

    @Configuration
    static class EmptyConfiguration {
    }
}

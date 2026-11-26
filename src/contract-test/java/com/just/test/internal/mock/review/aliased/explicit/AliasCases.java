package com.just.test.internal.mock.review.aliased.explicit;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.internal.mock.review.BindingConfig;
import com.just.test.internal.mock.review.BindingConfig.Mailer;
import com.just.test.lifecycle.JustTestLifecycle;
import com.just.test.annotation.JustMock;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@JustTest
@ContextConfiguration(classes = BindingConfig.Aliased.class)
public class AliasCases implements JustTestLifecycle {

    @JustMock(name = "legacyMailer")
    private Mailer target;

    @CaseSource
    void bindsExpectedBean(CaseContext caseContext) {
        assertNotNull(target);
    }
}

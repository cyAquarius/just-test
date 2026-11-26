package com.just.test.internal.mock.review.primary.plain;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.internal.mock.review.BindingConfig;
import com.just.test.internal.mock.review.BindingConfig.Mailer;
import com.just.test.lifecycle.JustTestLifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;

@JustTest
@ContextConfiguration(classes = BindingConfig.PrimaryComponent.class)
public class PrimaryPlainCases implements JustTestLifecycle {

    @Autowired
    private Mailer target;

    @CaseSource
    void bindsExpectedBean(CaseContext caseContext) {
        assertEquals("primary", target.send());
    }
}

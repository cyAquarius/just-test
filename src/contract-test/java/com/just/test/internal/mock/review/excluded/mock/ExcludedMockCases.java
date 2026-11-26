package com.just.test.internal.mock.review.excluded.mock;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.internal.mock.review.BindingConfig;
import com.just.test.internal.mock.review.BindingConfig.Mailer;
import com.just.test.lifecycle.JustTestLifecycle;
import com.just.test.annotation.JustMock;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertNull;

@JustTest
@ContextConfiguration(classes = BindingConfig.Excluded.class)
public class ExcludedMockCases implements JustTestLifecycle {

    @JustMock
    private Mailer target;

    @CaseSource
    void bindsExpectedBean(CaseContext caseContext) {
        assertNull(target.send());
    }
}

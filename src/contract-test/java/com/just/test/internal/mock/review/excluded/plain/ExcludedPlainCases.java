package com.just.test.internal.mock.review.excluded.plain;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.internal.mock.review.BindingConfig;
import com.just.test.internal.mock.review.BindingConfig.Mailer;
import com.just.test.lifecycle.JustTestLifecycle;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertNull;

@JustTest
@ContextConfiguration(classes = BindingConfig.Excluded.class)
public class ExcludedPlainCases implements JustTestLifecycle {

    @Autowired
    private ObjectProvider<Mailer> target;

    @CaseSource
    void bindsExpectedBean(CaseContext caseContext) {
        assertNull(target.getIfAvailable());
    }
}

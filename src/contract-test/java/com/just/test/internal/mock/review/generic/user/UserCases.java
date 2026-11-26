package com.just.test.internal.mock.review.generic.user;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.internal.mock.review.BindingConfig;
import com.just.test.internal.mock.review.BindingConfig.Repository;
import com.just.test.internal.mock.review.BindingConfig.User;
import com.just.test.lifecycle.JustTestLifecycle;
import com.just.test.annotation.JustSpy;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;

@JustTest
@ContextConfiguration(classes = BindingConfig.Generic.class)
public class UserCases implements JustTestLifecycle {

    @JustSpy
    private Repository<User> target;

    @CaseSource
    void bindsExpectedBean(CaseContext caseContext) {
        assertEquals("users", target.kind());
    }
}

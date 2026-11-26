package com.just.test.internal.mock.review.lifecycle.plain;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.internal.mock.review.lifecycle.LifecycleMockConfig;
import com.just.test.internal.mock.review.lifecycle.LifecycleMockConfig.Intercepted;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;

@JustTest
@ContextConfiguration(classes = LifecycleMockConfig.class)
public class LifecyclePlainCases extends LifecycleMockConfig.Support {

    @Autowired
    private Intercepted intercepted;

    @CaseSource
    void realBeansKeepTheirContextLifecycle(CaseContext context) {
        assertEquals("open", spied.read());
        assertEquals("open", intercepted.read());
        counters.assertRealBeansOpen();
    }
}

package com.just.test.internal.mock.review.lifecycle.mock;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.internal.mock.review.lifecycle.LifecycleMockConfig;
import com.just.test.internal.mock.review.lifecycle.LifecycleMockConfig.Intercepted;
import com.just.test.annotation.JustMock;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@JustTest
@ContextConfiguration(classes = LifecycleMockConfig.class)
public class LifecycleMockCases extends LifecycleMockConfig.Support {

    @JustMock
    private Intercepted intercepted;

    @CaseSource
    void realBeansKeepTheirContextLifecycle(CaseContext context) {
        assertEquals("open", spied.read());
        when(intercepted.read()).thenReturn("stub");
        assertEquals("stub", intercepted.read());
        counters.assertRealBeansOpen();
    }
}

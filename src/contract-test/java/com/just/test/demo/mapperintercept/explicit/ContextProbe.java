package com.just.test.demo.mapperintercept.explicit;

import org.springframework.context.ApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

public final class ContextProbe {
    private static ApplicationContext shared;
    public static synchronized void check(ApplicationContext context) {
        if (shared != null) assertSame(shared, context);
        shared = context;
        assertTrue(context.containsBean("justMock.intercept.target.sampleMapper"));
        assertFalse(context.containsBean("sampleMapper#0"));
    }
}

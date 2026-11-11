package com.just.test.mock.intercept;

import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/** 不同真实 Bean {@code @JustMock} 子集，以及未声明 {@code @JustMock} 的类，是否共用 Context。 */
public final class InterceptContextProbe {

    private static final List<ApplicationContext> SHARED = new ArrayList<ApplicationContext>();
    private static final Object LOCK = new Object();
    private static ApplicationContext spyContext;

    private InterceptContextProbe() {
    }

    public static void share(ApplicationContext context) {
        synchronized (LOCK) {
            if (spyContext != null) {
                assertNotSame(spyContext, context);
            }
            for (ApplicationContext existing : SHARED) {
                assertSame(existing, context);
            }
            SHARED.add(context);
        }
    }

    public static void distinctSpy(ApplicationContext context) {
        synchronized (LOCK) {
            spyContext = context;
            for (ApplicationContext existing : SHARED) {
                assertNotSame(existing, context);
            }
        }
    }
}

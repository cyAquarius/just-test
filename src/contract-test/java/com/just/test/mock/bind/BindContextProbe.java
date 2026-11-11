package com.just.test.mock.bind;

import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/** 记录绑定用例与真实 Bean 用例是否共用同一个 ApplicationContext。 */
public final class BindContextProbe {

    private static final List<ApplicationContext> SHARED = new ArrayList<ApplicationContext>();
    private static final Object LOCK = new Object();
    private static ApplicationContext real;

    private BindContextProbe() {
    }

    public static void share(ApplicationContext context) {
        synchronized (LOCK) {
            if (real != null) {
                assertNotSame(real, context);
            }
            for (ApplicationContext existing : SHARED) {
                assertSame(existing, context);
            }
            SHARED.add(context);
        }
    }

    public static void distinctRealBean(ApplicationContext context) {
        synchronized (LOCK) {
            real = context;
            for (ApplicationContext existing : SHARED) {
                assertNotSame(existing, context);
            }
        }
    }
}

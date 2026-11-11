package com.just.test.demo.project.feignautoapp.bind;

import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;

/** 不同 Feign {@code @JustMock} 子集必须落到同一个 ApplicationContext。 */
public final class FeignBindProbe {

    private static final List<ApplicationContext> SHARED = new ArrayList<ApplicationContext>();
    private static final Object LOCK = new Object();

    private FeignBindProbe() {
    }

    public static void share(ApplicationContext context) {
        synchronized (LOCK) {
            for (ApplicationContext existing : SHARED) {
                assertSame(existing, context);
            }
            SHARED.add(context);
        }
    }
}

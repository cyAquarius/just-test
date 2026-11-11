package com.just.test.mock.interceptbean;

import com.just.test.internal.mock.JustMockInterceptRegistry;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 接口、实现、未声明三类是否共用一个 Context，以及该 Bean 是否只拦截了一次。 */
public final class SameBeanContextProbe {

    private static final List<ApplicationContext> SHARED = new ArrayList<ApplicationContext>();
    private static final Object LOCK = new Object();

    private SameBeanContextProbe() {
    }

    public static void share(ApplicationContext context) {
        synchronized (LOCK) {
            for (ApplicationContext existing : SHARED) {
                assertSame(existing, context);
            }
            SHARED.add(context);
        }
    }

    public static void assertRegisteredOnce(ApplicationContext context, String beanName) {
        ConfigurableListableBeanFactory factory =
                ((ConfigurableApplicationContext) context).getBeanFactory();
        String hidden = "justMock.intercept.target." + beanName;
        assertTrue(factory.containsBeanDefinition(hidden), hidden);
        assertFalse(factory.containsBeanDefinition("scopedTarget." + hidden), hidden);
        int marks = 0;
        for (String name : factory.getBeanDefinitionNames()) {
            if (name.contains(hidden)) {
                marks++;
            }
        }
        assertEquals(1, marks, beanName);
        assertEquals(Collections.singleton(beanName),
                context.getBean(JustMockInterceptRegistry.class).getBeanNames());
    }

    public static void assertBound(Object field, ApplicationContext context, String beanName) throws Exception {
        Object bean = context.getBean(beanName);
        if (bean instanceof Advised) {
            bean = ((Advised) bean).getTargetSource().getTarget();
        }
        assertSame(field, bean);
    }
}

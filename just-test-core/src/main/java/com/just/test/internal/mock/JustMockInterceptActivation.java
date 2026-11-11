package com.just.test.internal.mock;

import com.just.test.annotation.JustMock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 当前线程上「本测试类声明了 {@code @JustMock}」的拦截 Bean。
 *
 * <p>激活看的是字段声明，不是当前有没有 stub。case 结束时清掉 stub 和激活集。
 * 未激活的调用由默认答案委托给真实 Bean。</p>
 */
public final class JustMockInterceptActivation {

    static final String SOURCE = "@JustMock intercept";

    private static final Logger log = LoggerFactory.getLogger(JustMockInterceptActivation.class);

    private static final ThreadLocal<Set<String>> ACTIVE = new ThreadLocal<Set<String>>() {
        @Override
        protected Set<String> initialValue() {
            return Collections.emptySet();
        }
    };

    private JustMockInterceptActivation() {
    }

    public static void activate(Object testInstance, ApplicationContext context) {
        if (testInstance == null || context == null) {
            ACTIVE.set(Collections.<String>emptySet());
            return;
        }
        JustMockInterceptRegistry registry = registry(context);
        if (registry == null || registry.getBeanNames().isEmpty()) {
            ACTIVE.set(Collections.<String>emptySet());
            return;
        }
        JustMockBindings bindings;
        try {
            bindings = context.getBean(JustMockBindings.class);
        } catch (NoSuchBeanDefinitionException ex) {
            ACTIVE.set(Collections.<String>emptySet());
            return;
        }
        Set<String> active = new LinkedHashSet<String>();
        for (Class<?> type = testInstance.getClass(); type != null && type != Object.class;
             type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                JustMock justMock = field.getAnnotation(JustMock.class);
                if (justMock == null) {
                    continue;
                }
                JustMockDefinition definition = JustMockDefinition.forField(field, justMock);
                String beanName = bindings.getBeanName(definition);
                if (beanName != null && registry.isIntercept(beanName)) {
                    active.add(beanName);
                }
            }
        }
        ACTIVE.set(Collections.unmodifiableSet(active));
    }

    /**
     * case 结束：先重置拦截 mock 上的 stub，再丢掉本线程的激活集。
     * 线程作用域随后会丢弃 mock 实例，下一个 case 拿到的是新 mock。
     */
    public static void resetStubsAndClear(ApplicationContext context) {
        try {
            if (context == null) {
                return;
            }
            JustMockInterceptRegistry registry = registry(context);
            if (registry == null) {
                return;
            }
            for (String beanName : registry.getBeanNames()) {
                resetBean(context, beanName);
            }
        } finally {
            ACTIVE.remove();
        }
    }

    static void bindActiveBeans(Set<String> beanNames) {
        if (beanNames == null || beanNames.isEmpty()) {
            ACTIVE.set(Collections.<String>emptySet());
            return;
        }
        ACTIVE.set(Collections.unmodifiableSet(new LinkedHashSet<String>(beanNames)));
    }

    static boolean isActive(String beanName) {
        return beanName != null && ACTIVE.get().contains(beanName);
    }

    private static void resetBean(ApplicationContext context, String beanName) {
        try {
            Object bean = context.getBean(beanName);
            if (bean instanceof Advised) {
                bean = ((Advised) bean).getTargetSource().getTarget();
            }
            if (bean != null && Mockito.mockingDetails(bean).isMock()) {
                Mockito.reset(bean);
            }
        } catch (RuntimeException ex) {
            log.debug("[JustMock] Could not reset intercept stub for '{}': {}", beanName, ex.getMessage());
        } catch (Exception ex) {
            log.debug("[JustMock] Could not reset intercept stub for '{}': {}", beanName, ex.getMessage());
        }
    }

    private static JustMockInterceptRegistry registry(ApplicationContext context) {
        try {
            return context.getBean(JustMockInterceptRegistry.class);
        } catch (NoSuchBeanDefinitionException ex) {
            return null;
        }
    }

    static final class AnswerSwitch implements Answer<Object> {
        private final String beanName;
        private final Answer<Object> delegate;

        AnswerSwitch(String beanName, Answer<Object> delegate) {
            this.beanName = beanName;
            this.delegate = delegate;
        }

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            if (isActive(beanName)) {
                return Mockito.RETURNS_DEFAULTS.answer(invocation);
            }
            return delegate.answer(invocation);
        }
    }
}

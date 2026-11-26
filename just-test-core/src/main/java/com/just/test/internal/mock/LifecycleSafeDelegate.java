package com.just.test.internal.mock;

import org.mockito.AdditionalAnswers;
import org.mockito.stubbing.Answer;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.ClassUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

final class LifecycleSafeDelegate {

    private LifecycleSafeDelegate() {
    }

    static Answer<Object> to(Object bean) {
        Answer<Object> delegate = AdditionalAnswers.delegatesTo(bean);
        Class<?> type = AopUtils.getTargetClass(bean);
        return invocation -> {
            Method method = invocation.getMethod();
            if (isLifecycle(method, type) || isLifecycle(ClassUtils.getMostSpecificMethod(method, type), type)) {
                return null;
            }
            return delegate.answer(invocation);
        };
    }

    private static boolean isLifecycle(Method method, Class<?> type) {
        if (method.getParameterCount() == 0) {
            String name = method.getName();
            if (("afterPropertiesSet".equals(name) && InitializingBean.class.isAssignableFrom(type))
                    || ("destroy".equals(name) && DisposableBean.class.isAssignableFrom(type))
                    || ("close".equals(name) && AutoCloseable.class.isAssignableFrom(type))) {
                return true;
            }
        }
        for (Annotation annotation : method.getDeclaredAnnotations()) {
            String name = annotation.annotationType().getName();
            if ("javax.annotation.PostConstruct".equals(name) || "javax.annotation.PreDestroy".equals(name)
                    || "jakarta.annotation.PostConstruct".equals(name) || "jakarta.annotation.PreDestroy".equals(name)) {
                return true;
            }
        }
        return false;
    }
}

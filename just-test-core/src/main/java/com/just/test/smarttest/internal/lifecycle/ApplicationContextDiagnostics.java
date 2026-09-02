package com.just.test.smarttest.internal.lifecycle;

import org.springframework.context.ApplicationContext;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** 记录 SmartTest case 实际使用过的 Spring Context，仅用于风险诊断。 */
final class ApplicationContextDiagnostics {

    private final Object monitor = new Object();
    private final List<WeakReference<ApplicationContext>> contexts = new ArrayList<>();
    private final Map<Class<?>, Boolean> failureWarnedTestClasses = new WeakHashMap<>();
    private boolean observedAnyContext;
    private boolean multipleContextsObserved;
    private boolean riskWarningEmitted;

    void observe(ApplicationContext context) {
        synchronized (monitor) {
            boolean known = false;
            for (int i = contexts.size() - 1; i >= 0; i--) {
                ApplicationContext existing = contexts.get(i).get();
                if (existing == null) {
                    contexts.remove(i);
                } else if (existing == context) {
                    known = true;
                }
            }
            if (!known) {
                if (observedAnyContext) {
                    multipleContextsObserved = true;
                }
                observedAnyContext = true;
                contexts.add(new WeakReference<>(context));
            }
        }
    }

    boolean hasMultipleContexts() {
        synchronized (monitor) {
            return multipleContextsObserved;
        }
    }

    boolean markRiskWarning() {
        synchronized (monitor) {
            if (!hasMultipleContexts() || riskWarningEmitted) {
                return false;
            }
            riskWarningEmitted = true;
            return true;
        }
    }

    boolean markFailureWarningFor(Class<?> testClass) {
        synchronized (monitor) {
            if (!hasMultipleContexts() || failureWarnedTestClasses.containsKey(testClass)) {
                return false;
            }
            failureWarnedTestClasses.put(testClass, Boolean.TRUE);
            return true;
        }
    }
}

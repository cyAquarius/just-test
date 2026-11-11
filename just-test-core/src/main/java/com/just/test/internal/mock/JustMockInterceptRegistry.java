package com.just.test.internal.mock;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 本次 Context 启动时已经装上的拦截 Bean 名。启动之后不再增加。
 */
public final class JustMockInterceptRegistry {

    private final Set<String> beanNames;

    public JustMockInterceptRegistry(Set<String> beanNames) {
        this.beanNames = Collections.unmodifiableSet(new LinkedHashSet<String>(beanNames));
    }

    public Set<String> getBeanNames() {
        return beanNames;
    }

    boolean isIntercept(String beanName) {
        return beanNames.contains(beanName);
    }
}

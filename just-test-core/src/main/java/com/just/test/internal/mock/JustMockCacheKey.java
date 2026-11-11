package com.just.test.internal.mock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfigurationAttributes;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 决定哪些 {@code @JustMock} 进入 Spring Test 上下文缓存 key。
 *
 * <p>已经能确定只会绑定到既有线程级 mock 的声明不改变 Bean 定义，因此不参与 key。
 * 使用 {@code @JustTestProject} 时，真实 Bean 的 {@code @JustMock} 走线程拦截，
 * 也不参与 key；没装上拦截的类型在启动或注入时 fail-fast，不会退回替换定义。
 * 与 {@code @JustSpy} 重叠的声明仍留在 key 里。没有 {@code @JustTestProject}
 * 且又不能确定是绑定时，保留声明，行为与原先一致。</p>
 */
final class JustMockCacheKey {

    private static final Logger log = LoggerFactory.getLogger(JustMockCacheKey.class);

    private JustMockCacheKey() {
    }

    static Set<JustMockDefinition> affecting(Class<?> testClass,
                                              List<ContextConfigurationAttributes> attributes,
                                              Set<JustMockDefinition> mocks,
                                              Set<JustMockDefinition> spies) {
        if (mocks == null || mocks.isEmpty() || testClass == null) {
            return mocks == null
                    ? Collections.<JustMockDefinition>emptySet()
                    : mocks;
        }
        JustMockKnownSources sources;
        try {
            sources = JustMockKnownSources.forTest(testClass, attributes);
        } catch (RuntimeException ex) {
            log.warn("[JustMock] Could not classify thread-scoped @JustMock bindings for {}; "
                    + "keeping them in the context cache key", testClass.getName(), ex);
            return mocks;
        }
        JustMockInterceptPlan intercepts = JustMockInterceptPlan.forTest(testClass, attributes);
        Set<JustMockDefinition> affecting = new LinkedHashSet<JustMockDefinition>();
        for (JustMockDefinition mock : mocks) {
            if (overlapsSpy(mock, spies)) {
                affecting.add(mock);
                continue;
            }
            if (sources.isConfidentBind(mock)) {
                log.debug("[JustMock] {} binds an existing thread-scoped mock and is omitted from the context cache key",
                        mock.describe());
                continue;
            }
            if (intercepts.isProject()) {
                log.debug("[JustMock] {} is a real-bean @JustMock on @JustTestProject {} and is omitted "
                                + "from the context cache key",
                        mock.describe(), intercepts.startupClass().getName());
                continue;
            }
            affecting.add(mock);
        }
        return Collections.unmodifiableSet(affecting);
    }

    /**
     * 与 {@code @JustSpy} 类型层级重叠的 {@code @JustMock} 必须留在 key 里，
     * 这样 spy/mock 冲突仍由声明了它的测试类在启动期失败，而不会复用只含 spy 的 Context。
     */
    private static boolean overlapsSpy(JustMockDefinition mock, Set<JustMockDefinition> spies) {
        if (spies == null || spies.isEmpty()) {
            return false;
        }
        Class<?> mockType = mock.getType();
        for (JustMockDefinition spy : spies) {
            Class<?> spyType = spy.getType();
            if (mockType == spyType
                    || mockType.isAssignableFrom(spyType)
                    || spyType.isAssignableFrom(mockType)) {
                return true;
            }
        }
        return false;
    }
}

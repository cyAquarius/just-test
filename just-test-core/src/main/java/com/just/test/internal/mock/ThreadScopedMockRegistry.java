package com.just.test.internal.mock;

import java.util.Collections;
import java.util.Set;

/**
 * 记录所有 thread-scoped mock 的 bean 名称，供 case 开始时统一预热。
 *
 * <p>解决 ScopedProxy 懒加载 + Mockito matcher 时序冲突：
 * 当测试代码执行 {@code when(proxy.method(anyMatcher())).thenReturn(...)} 时，
 * matcher 先 push 栈 → proxy 方法触发首次懒加载 → {@code Mockito.mock()} 检测
 * matcher 栈残留 → 抛 {@code InvalidUseOfMatchersException}。</p>
 *
 * <p>通过在 case 开始时（{@code ThreadScope.resetCurrentThread()} 之后、用户代码之前）
 * 预热所有 thread-scoped mock，确保真正执行 {@code when()} 时 target 已经存在，不会触发懒加载。</p>
 */
public class ThreadScopedMockRegistry {

    private final Set<String> beanNames;

    public ThreadScopedMockRegistry(Set<String> beanNames) {
        this.beanNames = Collections.unmodifiableSet(beanNames);
    }

    public Set<String> getBeanNames() {
        return beanNames;
    }
}

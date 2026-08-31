package com.just.test.smarttest.scope;

import com.just.test.smarttest.context.CaseExecutionContext;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 线程级 Spring Scope，每个线程持有独立的 bean 实例。
 *
 * <p>Mock 按工作线程隔离；{@code SmartTestRoutingDataSource} 则按执行 case
 * 隔离数据库。二者都在 case 生命周期开始和结束时清理；没有活动 case 的线程
 * 无法创建 scoped mock，避免线程池残留未受管理的实例。</p>
 *
 * <p>配合 {@code ScopedProxyMode.TARGET_CLASS} 使用，注入点拿到的是 CGLIB 代理，
 * 每次方法调用委托给当前线程的实例，when() stubbing 天然线程隔离。</p>
 *
 * <p>每个 JUnit 工作线程独立持有实例，任务切换线程时不会继承其他 case 的 mock。</p>
 */
public class ThreadScope implements Scope {

    private static final ThreadLocal<Map<String, Object>> SCOPE_MAP =
            new ThreadLocal<Map<String, Object>>() {
                @Override
                protected Map<String, Object> initialValue() {
                    return new HashMap<>();
                }
            };

    private static final ThreadLocal<Map<String, Runnable>> DESTRUCTION_CALLBACKS =
            ThreadLocal.withInitial(HashMap::new);
    private final String scopeKey = UUID.randomUUID().toString();

    @Override
    public Object get(String name, ObjectFactory<?> objectFactory) {
        if (!CaseExecutionContext.hasActiveCase()) {
            throw new IllegalStateException("[SmartTest] Thread-scoped mock access requires an active SmartTest case. "
                    + "Asynchronous work does not inherit this context; do not access SmartTest mocks from it.");
        }
        return SCOPE_MAP.get().computeIfAbsent(qualifiedName(name), k -> objectFactory.getObject());
    }

    @Override
    public Object remove(String name) {
        String qualifiedName = qualifiedName(name);
        DESTRUCTION_CALLBACKS.get().remove(qualifiedName);
        return SCOPE_MAP.get().remove(qualifiedName);
    }

    /**
     * 清空当前线程所有 scoped bean。每个 case 执行前调用，确保 mock 状态干净。
     */
    public static void resetCurrentThread() {
        Map<String, Runnable> callbacks = DESTRUCTION_CALLBACKS.get();
        RuntimeException cleanupFailure = null;
        try {
            for (Runnable callback : callbacks.values()) {
                try {
                    callback.run();
                } catch (RuntimeException e) {
                    if (cleanupFailure == null) {
                        cleanupFailure = e;
                    } else {
                        cleanupFailure.addSuppressed(e);
                    }
                }
            }
        } finally {
            DESTRUCTION_CALLBACKS.remove();
            SCOPE_MAP.remove();
        }
        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }

    public static void clearCurrentThread() {
        resetCurrentThread();
    }

    @Override
    public void registerDestructionCallback(String name, Runnable callback) {
        DESTRUCTION_CALLBACKS.get().put(qualifiedName(name), callback);
    }

    @Override
    public Object resolveContextualObject(String key) {
        return null;
    }

    @Override
    public String getConversationId() {
        return Thread.currentThread().getName();
    }

    private String qualifiedName(String name) {
        return scopeKey + ":" + name;
    }
}

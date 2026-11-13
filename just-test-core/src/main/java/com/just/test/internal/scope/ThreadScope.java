package com.just.test.internal.scope;

import com.just.test.internal.context.CaseExecutionContext;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 线程级 Spring Scope，每个线程持有独立的 bean 实例。
 *
 * <p>Mock 按工作线程隔离；{@code JustTestRoutingDataSource} 则按执行 case
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

    /**
     * 每个工作线程一个递增 id，在该线程的生命周期内保持不变。
     * 不用线程名：线程名不保证唯一，改名之后也不稳定。
     */
    private static final AtomicLong CONVERSATION_SEQUENCE = new AtomicLong();
    private static final ThreadLocal<String> CONVERSATION_ID = ThreadLocal.withInitial(
            () -> Long.toString(CONVERSATION_SEQUENCE.incrementAndGet()));

    private final String scopeKey = UUID.randomUUID().toString();

    @Override
    public Object get(String name, ObjectFactory<?> objectFactory) {
        if (!CaseExecutionContext.hasActiveCase()) {
            throw new IllegalStateException("[JustTest] Thread-scoped mock access requires an active JustTest case. "
                    + "Asynchronous work does not inherit this context; do not access JustTest mocks from it.");
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
        // 先复制再执行。回调里再注册或移除 callback 时，不能在正在迭代的 Map 上抛 ConcurrentModificationException。
        List<Runnable> snapshot = new ArrayList<Runnable>(callbacks.values());
        RuntimeException cleanupFailure = null;
        try {
            for (Runnable callback : snapshot) {
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
        return CONVERSATION_ID.get();
    }

    private String qualifiedName(String name) {
        return scopeKey + ":" + name;
    }
}

package com.just.test.mock;

import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.exceptions.base.MockitoException;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.CALLS_REAL_METHODS;

/**
 * 当前 case 的静态 Mock 作用域。
 *
 * <p>静态 Mock 仅对创建它的线程生效，从用户的 JUnit {@code @BeforeEach} 前持续到
 * {@code @AfterEach} 后，并由 JustTest 自动关闭。</p>
 */
public final class StaticMockContext implements AutoCloseable {

    private final ApplicationContext applicationContext;
    private final Thread ownerThread = Thread.currentThread();
    private final Map<Class<?>, MockedStatic<?>> mocks = new LinkedHashMap<>();
    private boolean closed;

    public StaticMockContext(ApplicationContext applicationContext) {
        if (applicationContext == null) {
            throw new IllegalArgumentException("applicationContext must not be null");
        }
        this.applicationContext = applicationContext;
    }

    public ApplicationContext getApplicationContext() {
        assertOwnerThread();
        assertOpen();
        return applicationContext;
    }

    /**
     * 为当前 case 线程创建静态 Mock。未显式 stub 的静态方法继续调用真实实现。
     */
    public <T> MockedStatic<T> mockStatic(Class<T> type) {
        assertOwnerThread();
        assertOpen();
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (mocks.containsKey(type)) {
            throw new IllegalStateException("[JustTest] Static mock already registered for " + type.getName());
        }
        try {
            MockedStatic<T> mocked = Mockito.mockStatic(type, CALLS_REAL_METHODS);
            mocks.put(type, mocked);
            return mocked;
        } catch (MockitoException e) {
            String detail = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
            throw new IllegalStateException(
                    "[JustTest] Failed to create inline static mock for " + type.getName()
                            + ". Possible causes include a missing inline mock maker, another static mock "
                            + "registered for this type on the current thread, or an unsupported target type. "
                            + "Mockito reported: " + detail, e);
        }
    }

    @Override
    public void close() {
        assertOwnerThread();
        if (closed) {
            return;
        }
        closed = true;

        Throwable failure = null;
        List<MockedStatic<?>> registered = new ArrayList<>(mocks.values());
        for (int i = registered.size() - 1; i >= 0; i--) {
            try {
                registered.get(i).close();
            } catch (Throwable t) {
                if (failure == null) {
                    failure = t;
                } else {
                    failure.addSuppressed(t);
                }
            }
        }
        mocks.clear();
        if (failure != null) {
            throw new IllegalStateException("[JustTest] Failed to close case static mocks", failure);
        }
    }

    private void assertOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("[JustTest] StaticMockContext must be used and closed on its case thread");
        }
    }

    private void assertOpen() {
        if (closed) {
            throw new IllegalStateException("[JustTest] StaticMockContext is already closed");
        }
    }
}

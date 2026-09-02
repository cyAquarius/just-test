package com.just.test.smarttest.internal.lifecycle;

import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.lifecycle.SmartTestLifecycle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class SmartTestExtensionTest {

    @Test
    void failsWhenJdbcTemplateIsAbsent() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.refresh();
        try {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> SmartTestExtension.requireJdbcTemplate(context));
            assertTrue(failure.getMessage().contains("JdbcTemplate is missing"));
            assertTrue(failure.getMessage().contains("configuration error"));
        } finally {
            context.close();
        }
    }

    @Test
    void failsWhenRoutingDataSourceIsAbsent() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.refresh();
        try {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> SmartTestExtension.requireRoutingDataSource(context));
            assertTrue(failure.getMessage().contains("SmartTestRoutingDataSource is missing"));
            assertTrue(failure.getMessage().contains("configuration error"));
        } finally {
            context.close();
        }
    }

    @Test
    void exposesAmbiguousJdbcTemplateConfiguration() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.getBeanFactory().registerSingleton("firstJdbcTemplate", new JdbcTemplate());
        context.getBeanFactory().registerSingleton("secondJdbcTemplate", new JdbcTemplate());
        context.refresh();
        try {
            assertThrows(NoUniqueBeanDefinitionException.class,
                    () -> SmartTestExtension.requireJdbcTemplate(context));
        } finally {
            context.close();
        }
    }

    @Test
    void invokesAfterExecuteThenVerifyBeforeRethrowingUnhandledException() throws Throwable {
        CaseContext caseContext = new CaseContext("unhandled",
                "com/just/test/smarttest/lifecycle/unhandled-missing");
        OrderedLifecycle lifecycle = new OrderedLifecycle();
        SmartTestExtension extension = new SmartTestExtension(caseContext);
        bindLifecycle(extension, lifecycle);

        IllegalStateException thrown = new IllegalStateException("boom");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> interceptThrowing(extension, lifecycle, thrown));

        assertSame(thrown, failure);
        assertEquals(1, lifecycle.afterExecuteCalls.get());
        assertSame(thrown, caseContext.getException());
        assertEquals("afterExecute, verifyException, verifyResult, verifyDatabase",
                String.join(", ", lifecycle.order));
    }

    @Test
    void afterExecuteFailureSuppressesOriginalTestException() throws Exception {
        CaseContext caseContext = new CaseContext("unhandled",
                "com/just/test/smarttest/lifecycle/unhandled-missing");
        SmartTestLifecycle lifecycle = new SmartTestLifecycle() {
            @Override
            public void afterExecute(CaseContext context) {
                throw new IllegalArgumentException("afterExecute failed");
            }
        };
        SmartTestExtension extension = new SmartTestExtension(caseContext);
        bindLifecycle(extension, lifecycle);

        IllegalStateException thrown = new IllegalStateException("boom");
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> interceptThrowing(extension, lifecycle, thrown));

        assertEquals("afterExecute failed", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertSame(thrown, failure.getSuppressed()[0]);
    }

    private static void interceptThrowing(SmartTestExtension extension,
                                          SmartTestLifecycle lifecycle,
                                          RuntimeException thrown) throws Throwable {
        @SuppressWarnings("unchecked")
        InvocationInterceptor.Invocation<Void> invocation = mock(InvocationInterceptor.Invocation.class);
        doThrow(thrown).when(invocation).proceed();
        @SuppressWarnings("unchecked")
        ReflectiveInvocationContext<Method> invocationContext = mock(ReflectiveInvocationContext.class);
        ExtensionContext extensionContext = mock(ExtensionContext.class);
        doReturn(lifecycle).when(extensionContext).getRequiredTestInstance();
        doReturn(lifecycle.getClass()).when(extensionContext).getRequiredTestClass();
        extension.interceptTestTemplateMethod(invocation, invocationContext, extensionContext);
    }

    private static void bindLifecycle(SmartTestExtension extension, SmartTestLifecycle lifecycle)
            throws Exception {
        Field field = SmartTestExtension.class.getDeclaredField("lifecycle");
        field.setAccessible(true);
        field.set(extension, lifecycle);
    }

    static final class OrderedLifecycle implements SmartTestLifecycle {
        final AtomicInteger afterExecuteCalls = new AtomicInteger();
        final List<String> order = new ArrayList<>();

        @Override
        public void afterExecute(CaseContext context) {
            afterExecuteCalls.incrementAndGet();
            order.add("afterExecute");
        }

        @Override
        public boolean verifyException(CaseContext context) {
            order.add("verifyException");
            return false;
        }

        @Override
        public boolean verifyResult(CaseContext context) {
            order.add("verifyResult");
            return true;
        }

        @Override
        public boolean verifyDatabase(CaseContext context, JdbcTemplate jdbcTemplate) {
            order.add("verifyDatabase");
            return true;
        }
    }
}

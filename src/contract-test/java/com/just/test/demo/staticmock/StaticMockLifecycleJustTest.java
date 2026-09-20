package com.just.test.demo.staticmock;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import com.just.test.mock.StaticMockContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@JustTest
@Execution(ExecutionMode.CONCURRENT)
@ContextConfiguration(classes = StaticMockLifecycleJustTest.EmptyConfiguration.class)
class StaticMockLifecycleJustTest implements JustTestLifecycle {

    private static final AtomicInteger BEFORE_EACH_CALLS = new AtomicInteger();
    private static final AtomicInteger AFTER_EACH_CALLS = new AtomicInteger();
    private static final ThreadLocal<String> PHASE = new ThreadLocal<>();

    @Autowired
    private ApplicationContext applicationContext;

    @Override
    public void configureStaticMocks(CaseContext context, StaticMockContext mocks) {
        assertNull(PHASE.get());
        PHASE.set("static-mocks");
        MockedStatic<StaticContextGateway> gateway = mocks.mockStatic(StaticContextGateway.class);
        gateway.when(StaticContextGateway::getApplicationContext)
                .thenReturn(mocks.getApplicationContext());
        gateway.when(StaticContextGateway::getCaseMarker)
                .thenReturn(context.getString("marker"));
    }

    @BeforeEach
    void staticMockIsAvailableInBeforeEach(CaseContext context) {
        assertEquals("static-mocks", PHASE.get());
        PHASE.set("junit-before-each");
        assertEquals(context.getString("marker"), StaticContextGateway.getCaseMarker());
        assertSame(applicationContext, StaticContextGateway.getApplicationContext());
        BEFORE_EACH_CALLS.incrementAndGet();
    }

    @CaseSource
    void bindsStaticGatewayToTheCurrentCase(CaseContext context) {
        assertEquals("lifecycle-before-execute", PHASE.get());
        PHASE.set("test-method");
        assertEquals(context.getString("marker"), StaticContextGateway.getCaseMarker());
        assertSame(applicationContext, StaticContextGateway.getApplicationContext());
    }

    @AfterEach
    void staticMockIsAvailableInAfterEach(CaseContext context) {
        assertEquals("lifecycle-after-execute", PHASE.get());
        PHASE.remove();
        assertEquals(context.getString("marker"), StaticContextGateway.getCaseMarker());
        assertSame(applicationContext, StaticContextGateway.getApplicationContext());
        AFTER_EACH_CALLS.incrementAndGet();
    }

    @AfterAll
    static void restoresStaticGatewayAfterCases() {
        assertEquals(2, BEFORE_EACH_CALLS.get());
        assertEquals(2, AFTER_EACH_CALLS.get());
        assertNull(StaticContextGateway.getApplicationContext());
        assertEquals("production", StaticContextGateway.getCaseMarker());
    }

    @Override
    public void beforeExecute(CaseContext context) {
        assertEquals("junit-before-each", PHASE.get());
        PHASE.set("lifecycle-before-execute");
    }

    @Override
    public void afterExecute(CaseContext context) {
        assertEquals("test-method", PHASE.get());
        PHASE.set("lifecycle-after-execute");
    }

    static class StaticContextGateway {

        static ApplicationContext getApplicationContext() {
            return null;
        }

        static String getCaseMarker() {
            return "production";
        }
    }

    @Configuration
    static class EmptyConfiguration {
    }
}

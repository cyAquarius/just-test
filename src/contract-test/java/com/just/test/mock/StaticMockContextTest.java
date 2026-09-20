package com.just.test.mock;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.context.support.GenericApplicationContext;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticMockContextTest {

    @Test
    void isolatesTheSameStaticGatewayPerCaseThreadAndRestoresItAfterClose() throws Exception {
        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.refresh();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            Future<String> first = executor.submit(
                    () -> mockedContext(applicationContext, "context-a", barrier));
            Future<String> second = executor.submit(
                    () -> mockedContext(applicationContext, "context-b", barrier));

            assertEquals("context-a", first.get());
            assertEquals("context-b", second.get());
            assertEquals("production-context", StaticContextGateway.getContext());
        } finally {
            executor.shutdownNow();
            applicationContext.close();
        }
    }

    @Test
    void rejectsDuplicateRegistrationInOneCase() {
        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.refresh();
        try (StaticMockContext mocks = new StaticMockContext(applicationContext)) {
            mocks.mockStatic(StaticContextGateway.class);

            assertThrows(IllegalStateException.class,
                    () -> mocks.mockStatic(StaticContextGateway.class));
        } finally {
            applicationContext.close();
        }
    }

    @Test
    void preservesMockitoReasonWhenTheThreadAlreadyHasAStaticMock() {
        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.refresh();
        try (MockedStatic<StaticContextGateway> ignored = Mockito.mockStatic(StaticContextGateway.class);
             StaticMockContext mocks = new StaticMockContext(applicationContext)) {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> mocks.mockStatic(StaticContextGateway.class));

            assertTrue(failure.getMessage().contains("another static mock registered"));
            assertTrue(failure.getMessage().contains(failure.getCause().getMessage()));
        } finally {
            applicationContext.close();
        }
    }

    @Test
    void rejectsAccessFromAnotherThread() throws Exception {
        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.refresh();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (StaticMockContext mocks = new StaticMockContext(applicationContext)) {
            Future<IllegalStateException> failure = executor.submit(() -> assertThrows(
                    IllegalStateException.class, mocks::getApplicationContext));

            assertEquals("[JustTest] StaticMockContext must be used and closed on its case thread",
                    failure.get().getMessage());
        } finally {
            executor.shutdownNow();
            applicationContext.close();
        }
    }

    @Test
    void cannotRepairARegistryCapturedBeforeTheCaseMockStarts() {
        StaticHandlerFactory.initialize();
        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.refresh();
        try (StaticMockContext mocks = new StaticMockContext(applicationContext)) {
            MockedStatic<StaticContextGateway> gateway = mocks.mockStatic(StaticContextGateway.class);
            gateway.when(StaticContextGateway::getContext).thenReturn("case-context");

            assertEquals("case-context", StaticContextGateway.getContext());
            assertEquals("production-context", StaticHandlerFactory.getCapturedContext());
        } finally {
            applicationContext.close();
        }
    }

    private String mockedContext(GenericApplicationContext applicationContext,
                                 String context, CyclicBarrier barrier) throws Exception {
        try (StaticMockContext mocks = new StaticMockContext(applicationContext)) {
            MockedStatic<StaticContextGateway> gateway = mocks.mockStatic(StaticContextGateway.class);
            gateway.when(StaticContextGateway::getContext).thenReturn(context);
            barrier.await();
            return StaticContextGateway.getContext();
        }
    }

    private static class StaticContextGateway {

        static String getContext() {
            return "production-context";
        }
    }

    private static class StaticHandlerFactory {

        private static String capturedContext;

        static void initialize() {
            capturedContext = StaticContextGateway.getContext();
        }

        static String getCapturedContext() {
            return capturedContext;
        }
    }
}

package com.just.test.internal.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

final class TransactionThreadCleanup {

    private static final Logger log = LoggerFactory.getLogger(TransactionThreadCleanup.class);
    private static final String SQL_SESSION_HOLDER_CLASS = "org.mybatis.spring.SqlSessionHolder";
    private static final String SQL_SESSION_CLASS = "org.apache.ibatis.session.SqlSession";

    Throwable cleanup(ApplicationContext context, String caseName, LeakPhase phase,
                      Throwable failure) {
        if (context == null) {
            return failure;
        }

        Map<Object, Object> remainingResources = new LinkedHashMap<>(TransactionSynchronizationManager.getResourceMap());
        TransactionThreadSnapshot beforeCleanup = snapshot(remainingResources);
        if (!beforeCleanup.isActualTransactionActive()
                && !beforeCleanup.isSynchronizationActive()
                && remainingResources.isEmpty()) {
            return failure;
        }

        IllegalStateException leakFailure = new IllegalStateException(leakMessage(
                caseName, phase, beforeCleanup, remainingResources));
        failure = appendFailure(failure, leakFailure);
        return quarantine(context, remainingResources, failure);
    }

    private TransactionThreadSnapshot snapshot(Map<Object, Object> resources) {
        boolean synchronizationActive = TransactionSynchronizationManager.isSynchronizationActive();
        return new TransactionThreadSnapshot(
                TransactionSynchronizationManager.isActualTransactionActive(),
                synchronizationActive,
                synchronizationCount(synchronizationActive),
                resources.keySet());
    }

    private Integer synchronizationCount(boolean synchronizationActive) {
        if (!synchronizationActive) {
            return Integer.valueOf(0);
        }
        try {
            return Integer.valueOf(TransactionSynchronizationManager.getSynchronizations().size());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Throwable quarantine(ApplicationContext context, Map<Object, Object> remainingResources,
                                 Throwable failure) {
        // Isolate confirmed leaked thread state; this is not a substitute for business rollback.
        try {
            TransactionSynchronizationManager.clear();
        } catch (Throwable cleanupFailure) {
            failure = appendCleanupFailure(failure, "Spring transaction thread state", cleanupFailure);
        }
        for (Map.Entry<Object, Object> entry : remainingResources.entrySet()) {
            Object resourceKey = entry.getKey();
            try {
                Object resource = TransactionSynchronizationManager.unbindResourceIfPossible(resourceKey);
                closeKnown(resource == null ? entry.getValue() : resource, context);
            } catch (Throwable cleanupFailure) {
                failure = appendCleanupFailure(failure,
                        "unexpected transaction resource '" + resourceKey + "'", cleanupFailure);
            }
        }
        return failure;
    }

    private void closeKnown(Object resource, ApplicationContext context) throws Exception {
        if (resource instanceof Connection) {
            closeConnectionResource(resource);
            return;
        }
        if (resource instanceof ConnectionHolder) {
            closeConnectionResource(resource);
            return;
        }
        Class<?> sqlSessionType = loadOptionalClass(SQL_SESSION_CLASS, context);
        if (sqlSessionType != null && sqlSessionType.isInstance(resource)) {
            closeSqlSessionResource(resource, context);
            return;
        }
        Class<?> sqlSessionHolderType = loadOptionalClass(SQL_SESSION_HOLDER_CLASS, context);
        if (sqlSessionHolderType != null && sqlSessionHolderType.isInstance(resource)) {
            closeSqlSessionResource(resource, context);
            return;
        }
        // Unknown resource types are intentionally left untouched after unbinding.
    }

    private void closeConnectionResource(Object resource) throws Exception {
        Connection connection = null;
        if (resource instanceof ConnectionHolder) {
            connection = ((ConnectionHolder) resource).getConnection();
        } else if (resource instanceof Connection) {
            connection = (Connection) resource;
        }
        if (connection != null) {
            connection.close();
        }
    }

    private void closeSqlSessionResource(Object resource, ApplicationContext context) throws Exception {
        if (resource == null) {
            return;
        }

        Class<?> sqlSessionType = loadOptionalClass(SQL_SESSION_CLASS, context);
        Object sqlSession = sqlSessionType != null && sqlSessionType.isInstance(resource)
                ? resource
                : null;
        if (sqlSession == null) {
            Class<?> sqlSessionHolderType = loadOptionalClass(SQL_SESSION_HOLDER_CLASS, context);
            if (sqlSessionHolderType != null && sqlSessionHolderType.isInstance(resource)) {
                Method getSqlSession = sqlSessionHolderType.getMethod("getSqlSession");
                sqlSession = getSqlSession.invoke(resource);
            }
        }
        if (sqlSession == null) {
            return;
        }
        if (sqlSessionType != null && sqlSessionType.isInstance(sqlSession)) {
            sqlSessionType.getMethod("close").invoke(sqlSession);
        } else if (sqlSession instanceof AutoCloseable) {
            ((AutoCloseable) sqlSession).close();
        }
    }

    private Class<?> loadOptionalClass(String className, ApplicationContext context) {
        ClassLoader classLoader = context.getClassLoader();
        if (classLoader == null) {
            classLoader = Thread.currentThread().getContextClassLoader();
        }
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException ignored) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException ignoredAgain) {
                return null;
            } catch (LinkageError ignoredAgain) {
                return null;
            }
        } catch (LinkageError ignored) {
            return null;
        }
    }

    private String leakMessage(String caseName, LeakPhase phase,
                               TransactionThreadSnapshot beforeCleanup,
                               Map<Object, Object> remainingResources) {
        return new StringBuilder(phase == LeakPhase.BEFORE_CASE
                ? "[JustTest] Residual Spring transaction state found before case: case="
                : "[JustTest] Unfinished Spring transaction leaked at case boundary: case=")
                .append(caseName == null ? "<unknown>" : caseName)
                .append(", thread=").append(Thread.currentThread().getName())
                .append(", actualTransactionActive=")
                .append(beforeCleanup.isActualTransactionActive())
                .append(", synchronizationActive=")
                .append(beforeCleanup.isSynchronizationActive())
                .append(", synchronizationCount=")
                .append(beforeCleanup.getSynchronizationCount() == null
                        ? "unavailable" : beforeCleanup.getSynchronizationCount())
                .append(", resourceKeysBeforeCleanup=").append(beforeCleanup.getResourceKeys())
                .append(", remainingResourceKeys=").append(remainingResources.keySet())
                .append(". Business code must commit or roll back transactions on every path; ")
                .append("move validation before getTransaction when possible. JustTest quarantined the ")
                .append("leaked thread state, but quarantine is not a substitute for business rollback.")
                .toString();
    }

    private Throwable appendCleanupFailure(Throwable failure, String resourceDescription,
                                            Throwable cleanupFailure) {
        log.warn("[JustTest] Failed to clean {}: {}", resourceDescription,
                cleanupFailure.getMessage(), cleanupFailure);
        return appendFailure(failure, cleanupFailure);
    }

    private Throwable appendFailure(Throwable failure, Throwable additional) {
        if (failure == null) {
            return additional;
        }
        failure.addSuppressed(additional);
        return failure;
    }
}

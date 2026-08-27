package com.just.test.smarttest.datasource;

import com.just.test.smarttest.context.CaseExecutionContext;
import com.just.test.smarttest.h2.H2FunctionRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 按执行 case 路由的 H2 数据源。每个 case 对应一个独立的 H2 内存数据库。
 *
 * <p>数据库数量 = 当前活动 case 数量。case 结束时会释放对应的内存数据库，
 * 因此线程复用不会复用上一个 case 的数据。</p>
 */
public class SmartTestRoutingDataSource extends AbstractDataSource implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SmartTestRoutingDataSource.class);

    private static final AtomicLong INSTANCE_SEQUENCE = new AtomicLong();
    private static final String SCHEMA_CLONE_PROPERTY = "smarttest.schema.clone";

    private final String urlTemplate;
    private final String instanceKey = "smarttest_" + INSTANCE_SEQUENCE.incrementAndGet();
    private final ConcurrentHashMap<String, SingleConnectionDataSource> dataSources = new ConcurrentHashMap<>();
    private final Set<String> initializedSchemas = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean destroyed = new AtomicBoolean();
    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();
    private final ThreadLocal<Boolean> caseLease = new ThreadLocal<>();
    private final Object templateLock = new Object();
    private final AtomicBoolean forceCloneFailureForTests = new AtomicBoolean();
    private final AtomicLong cloneCount = new AtomicLong();
    private final AtomicLong fallbackCount = new AtomicLong();
    private SingleConnectionDataSource templateDataSource;
    private List<String> templateSchema;

    /**
     * @param urlTemplate H2 URL 模板，{key} 占位符在运行时替换为 case 标识。
     *                    示例：jdbc:h2:mem:{key};MODE=MySQL
     */
    public SmartTestRoutingDataSource(String urlTemplate) {
        this.urlTemplate = urlTemplate;
    }

    /**
     * 获取当前 case 对应的数据库标识（用于 SchemaInitializer 判断是否已初始化）。
     */
    public String currentDbKey() {
        lifecycleLock.readLock().lock();
        try {
            assertNotDestroyed();
            return instanceKey + "_" + sanitizeCaseId(CaseExecutionContext.requireCaseId());
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return resolve().getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return resolve().getConnection(username, password);
    }

    private SingleConnectionDataSource resolve() {
        lifecycleLock.readLock().lock();
        try {
            String key = currentDbKey();
            return dataSources.compute(key, (k, existing) -> {
                if (existing != null && isUsable(existing)) {
                    return existing;
                }
                if (existing != null) {
                    initializedSchemas.remove(k);
                    try {
                        existing.destroy();
                    } catch (Exception e) {
                        log.debug("[SmartTest] Failed to destroy unusable H2 database for case [{}]", k, e);
                    }
                }
                return createDataSource(k);
            });
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    /**
     * 将当前线程的 SmartTest case 注册为活动使用方。ApplicationContext 销毁会等待
     * 已注册的 case 释放数据库，避免关闭后的旧引用重新创建空 H2。
     */
    public void beginCase() {
        if (caseLease.get() != null) {
            throw new IllegalStateException("[SmartTest] A database case is already active on this thread.");
        }
        lifecycleLock.readLock().lock();
        try {
            assertNotDestroyed();
            caseLease.set(Boolean.TRUE);
        } catch (RuntimeException e) {
            lifecycleLock.readLock().unlock();
            throw e;
        }
    }

    boolean beginSchemaInitialization() {
        return initializedSchemas.add(currentDbKey());
    }

    void initializeCaseSchema(JdbcTemplate jdbcTemplate, List<String> cleanedDdls) {
        lifecycleLock.readLock().lock();
        try {
            assertNotDestroyed();
            if (!isSchemaCloneEnabled()) {
                SchemaInitializer.executeCachedDdl(jdbcTemplate, cleanedDdls);
                H2FunctionRegistrar.register(jdbcTemplate);
                return;
            }

            synchronized (templateLock) {
                ensureTemplate(cleanedDdls);
                try {
                    cloneTemplate(jdbcTemplate);
                    H2FunctionRegistrar.register(jdbcTemplate);
                    cloneCount.incrementAndGet();
                } catch (Exception cloneFailure) {
                    log.warn("[SmartTest] Failed to clone schema for case [{}], "
                                    + "falling back to cached DDL replay",
                            currentDbKey(), cloneFailure);
                    recreateCurrentCaseDataSource();
                    SchemaInitializer.executeCachedDdl(jdbcTemplate, cleanedDdls);
                    H2FunctionRegistrar.register(jdbcTemplate);
                    fallbackCount.incrementAndGet();
                }
            }
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    void schemaInitializationFailed() {
        lifecycleLock.readLock().lock();
        try {
            String key = currentDbKey();
            initializedSchemas.remove(key);
            SingleConnectionDataSource dataSource = dataSources.remove(key);
            if (dataSource != null) {
                dataSource.destroy();
            }
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    public void releaseCurrentCase() {
        boolean ownsLease = caseLease.get() != null;
        if (!ownsLease) {
            lifecycleLock.readLock().lock();
        }
        try {
            String key = currentDbKey();
            SingleConnectionDataSource dataSource = dataSources.remove(key);
            initializedSchemas.remove(key);
            if (dataSource != null) {
                dataSource.destroy();
            }
        } finally {
            if (ownsLease) {
                caseLease.remove();
            }
            lifecycleLock.readLock().unlock();
        }
    }

    @Override
    public void destroy() {
        lifecycleLock.writeLock().lock();
        try {
            if (!destroyed.compareAndSet(false, true)) {
                return;
            }
            dataSources.values().forEach(SingleConnectionDataSource::destroy);
            dataSources.clear();
            initializedSchemas.clear();
            synchronized (templateLock) {
                destroyTemplate();
            }
        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    private void assertNotDestroyed() {
        if (destroyed.get()) {
            throw new IllegalStateException("[SmartTest] Database access attempted after its ApplicationContext was destroyed.");
        }
    }

    private SingleConnectionDataSource createDataSource(String key) {
        return createDataSource(key, urlTemplate.replace("{key}", key));
    }

    private SingleConnectionDataSource createDataSource(String key, String url) {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setSuppressClose(true);
        log.info("[SmartTest] Created H2 database for case [{}]: {}", key, url);
        return dataSource;
    }

    private void ensureTemplate(List<String> cleanedDdls) {
        if (templateDataSource != null
                && templateSchema.equals(cleanedDdls)
                && isUsable(templateDataSource)) {
            return;
        }

        destroyTemplate();
        SingleConnectionDataSource candidate = createDataSource(
                instanceKey + "_template", templateUrl());
        try {
            JdbcTemplate templateJdbcTemplate = new JdbcTemplate(candidate);
            SchemaInitializer.executeCachedDdl(templateJdbcTemplate, cleanedDdls);
            H2FunctionRegistrar.register(templateJdbcTemplate);
            templateDataSource = candidate;
            templateSchema = Collections.unmodifiableList(new ArrayList<>(cleanedDdls));
            log.info("[SmartTest] Initialized schema template for data source [{}]", instanceKey);
        } catch (RuntimeException failure) {
            clearTemplateObjects(candidate);
            candidate.destroy();
            throw failure;
        }
    }

    private void clearTemplateObjects(SingleConnectionDataSource dataSource) {
        try {
            new JdbcTemplate(dataSource).execute("DROP ALL OBJECTS");
        } catch (Exception cleanupFailure) {
            log.debug("[SmartTest] Failed to clear schema template", cleanupFailure);
        }
    }

    private void cloneTemplate(JdbcTemplate caseJdbcTemplate) {
        if (forceCloneFailureForTests.compareAndSet(true, false)) {
            throw new IllegalStateException("Forced schema clone failure");
        }
        if (templateDataSource == null) {
            throw new IllegalStateException("Schema template is not available");
        }

        JdbcTemplate templateJdbcTemplate = new JdbcTemplate(templateDataSource);
        List<String> script = templateJdbcTemplate.execute(
                (ConnectionCallback<List<String>>) connection -> {
                    List<String> statements = new ArrayList<>();
                    try (Statement statement = connection.createStatement();
                         ResultSet resultSet = statement.executeQuery("SCRIPT SIMPLE NOSETTINGS")) {
                        while (resultSet.next()) {
                            String sql = resultSet.getString(1);
                            if (sql != null && !sql.trim().isEmpty()) {
                                statements.add(sql);
                            }
                        }
                    }
                    return statements;
                });
        if (script == null || script.isEmpty()) {
            throw new IllegalStateException("Schema template produced an empty clone script");
        }

        caseJdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                for (String sql : script) {
                    statement.execute(sql);
                }
            }
            return null;
        });
    }

    private void recreateCurrentCaseDataSource() {
        String key = currentDbKey();
        SingleConnectionDataSource dataSource = dataSources.remove(key);
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    private void destroyTemplate() {
        if (templateDataSource != null) {
            SingleConnectionDataSource dataSource = templateDataSource;
            templateDataSource = null;
            clearTemplateObjects(dataSource);
            dataSource.destroy();
        }
        templateSchema = null;
    }

    private String templateUrl() {
        String url = urlTemplate.replace("{key}", instanceKey + "_template");
        String upperCaseUrl = url.toUpperCase(Locale.ROOT);
        if (upperCaseUrl.contains("DB_CLOSE_DELAY")) {
            return url;
        }
        return url + (url.endsWith(";") ? "" : ";") + "DB_CLOSE_DELAY=-1";
    }

    private boolean isSchemaCloneEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty(SCHEMA_CLONE_PROPERTY, "true"));
    }

    void failNextSchemaCloneForTests() {
        forceCloneFailureForTests.set(true);
    }

    long schemaCloneCountForTests() {
        return cloneCount.get();
    }

    long schemaFallbackCountForTests() {
        return fallbackCount.get();
    }

    private boolean isUsable(SingleConnectionDataSource dataSource) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            return connection != null && !connection.isClosed() && connection.isValid(1);
        } catch (Exception e) {
            log.warn("[SmartTest] Replacing unusable H2 database for case", e);
            return false;
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    log.debug("[SmartTest] Failed to close H2 connection health-check handle", e);
                }
            }
        }
    }

    /**
     * 清理线程名中不适合作为数据库名的字符。
     * H2 内存数据库名只允许字母、数字、下划线。
     */
    private static String sanitizeCaseId(String name) {
        return name.replaceAll("[^a-zA-Z0-9]", "_");
    }
}

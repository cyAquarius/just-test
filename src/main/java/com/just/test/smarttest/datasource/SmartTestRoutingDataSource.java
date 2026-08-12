package com.just.test.smarttest.datasource;

import com.just.test.smarttest.context.CaseExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 按执行 case 路由的 H2 数据源。每个 case 对应一个独立的 H2 内存数据库。
 *
 * <p>数据库数量 = 当前活动 case 数量。case 结束时会释放对应的内存数据库，
 * 因此线程复用不会复用上一个 case 的数据。</p>
 */
public class SmartTestRoutingDataSource extends AbstractDataSource implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SmartTestRoutingDataSource.class);

    private static final AtomicLong INSTANCE_SEQUENCE = new AtomicLong();

    private final String urlTemplate;
    private final String instanceKey = "smarttest_" + INSTANCE_SEQUENCE.incrementAndGet();
    private final ConcurrentHashMap<String, SingleConnectionDataSource> dataSources = new ConcurrentHashMap<>();
    private final Set<String> initializedSchemas = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean destroyed = new AtomicBoolean();

    /**
     * @param urlTemplate H2 URL 模板，{key} 占位符在运行时替换为 case 标识。
     *                    示例：jdbc:h2:mem:{key};MODE=MySQL;DB_CLOSE_DELAY=-1
     */
    public SmartTestRoutingDataSource(String urlTemplate) {
        this.urlTemplate = urlTemplate;
    }

    /**
     * 获取当前 case 对应的数据库标识（用于 SchemaInitializer 判断是否已初始化）。
     */
    public String currentDbKey() {
        assertNotDestroyed();
        return instanceKey + "_" + sanitizeCaseId(CaseExecutionContext.requireCaseId());
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
        String key = currentDbKey();
        return dataSources.computeIfAbsent(key, k -> {
            String url = urlTemplate.replace("{key}", k);
            SingleConnectionDataSource ds = new SingleConnectionDataSource();
            ds.setDriverClassName("org.h2.Driver");
            ds.setUrl(url);
            ds.setUsername("sa");
            ds.setPassword("");
            ds.setSuppressClose(true);
            log.info("[SmartTest] Created H2 database for case [{}]: {}", k, url);
            return ds;
        });
    }

    boolean beginSchemaInitialization() {
        return initializedSchemas.add(currentDbKey());
    }

    void schemaInitializationFailed() {
        initializedSchemas.remove(currentDbKey());
    }

    public void releaseCurrentCase() {
        String key = currentDbKey();
        SingleConnectionDataSource dataSource = dataSources.remove(key);
        initializedSchemas.remove(key);
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    @Override
    public void destroy() {
        if (!destroyed.compareAndSet(false, true)) {
            return;
        }
        dataSources.values().forEach(SingleConnectionDataSource::destroy);
        dataSources.clear();
        initializedSchemas.clear();
    }

    private void assertNotDestroyed() {
        if (destroyed.get()) {
            throw new IllegalStateException("[SmartTest] Database access attempted after its ApplicationContext was destroyed.");
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

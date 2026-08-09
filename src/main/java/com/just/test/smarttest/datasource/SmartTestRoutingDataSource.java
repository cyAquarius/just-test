package com.just.test.smarttest.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 按线程路由的 H2 数据源。每个线程对应一个独立的 H2 内存数据库，
 * 实现「不同类并行，同一类方法串行」时的数据隔离。
 *
 * <p>数据库数量 = JUnit 5 parallelism（线程池大小），而非测试类数量。
 * 线程复用时自动复用同一个数据库，由 Listener 的 TRUNCATE 清理残留数据。</p>
 *
 * <p>未配置并行时，所有类在同一线程执行，只创建 1 个数据库，等价于原单库行为。</p>
 */
public class SmartTestRoutingDataSource extends AbstractDataSource implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SmartTestRoutingDataSource.class);

    private static final AtomicLong INSTANCE_SEQUENCE = new AtomicLong();

    private final String urlTemplate;
    private final String instanceKey = "smarttest_" + INSTANCE_SEQUENCE.incrementAndGet();
    private final ConcurrentHashMap<String, SingleConnectionDataSource> dataSources = new ConcurrentHashMap<>();
    private final Set<String> initializedSchemas = ConcurrentHashMap.newKeySet();

    /**
     * @param urlTemplate H2 URL 模板，{key} 占位符在运行时替换为线程标识。
     *                    示例：jdbc:h2:mem:{key};MODE=MySQL;DB_CLOSE_DELAY=-1
     */
    public SmartTestRoutingDataSource(String urlTemplate) {
        this.urlTemplate = urlTemplate;
    }

    /**
     * 获取当前线程对应的数据库标识（用于 SchemaInitializer 判断是否已初始化）。
     */
    public String currentDbKey() {
        return instanceKey + "_" + sanitizeThreadName(Thread.currentThread().getName());
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
            log.info("[SmartTest] Created H2 database for thread [{}]: {}", k, url);
            return ds;
        });
    }

    boolean beginSchemaInitialization() {
        return initializedSchemas.add(currentDbKey());
    }

    void schemaInitializationFailed() {
        initializedSchemas.remove(currentDbKey());
    }

    @Override
    public void destroy() {
        dataSources.values().forEach(SingleConnectionDataSource::destroy);
        dataSources.clear();
        initializedSchemas.clear();
    }

    /**
     * 清理线程名中不适合作为数据库名的字符。
     * H2 内存数据库名只允许字母、数字、下划线。
     */
    private static String sanitizeThreadName(String name) {
        return name.replaceAll("[^a-zA-Z0-9]", "_");
    }
}

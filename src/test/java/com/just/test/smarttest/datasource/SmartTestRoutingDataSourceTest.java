package com.just.test.smarttest.datasource;

import com.just.test.smarttest.loader.DataSetLoader;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmartTestRoutingDataSourceTest {

    private static final String URL = "jdbc:h2:mem:{key};MODE=MySQL;DB_CLOSE_DELAY=-1";

    @Test
    void isolatesDatabasesAcrossApplicationContexts() {
        SmartTestRoutingDataSource first = new SmartTestRoutingDataSource(URL);
        SmartTestRoutingDataSource second = new SmartTestRoutingDataSource(URL);
        try {
            assertNotEquals(first.currentDbKey(), second.currentDbKey());
        } finally {
            first.destroy();
            second.destroy();
        }
    }

    @Test
    void initializesAllSchemaResourcesAndCleansEveryTable() {
        SmartTestRoutingDataSource dataSource = new SmartTestRoutingDataSource(URL);
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            SchemaInitializer.initialize(jdbcTemplate,
                    "classpath:sql/schema-part-one.sql", "classpath:sql/schema-part-two.sql");
            jdbcTemplate.execute("CREATE TABLE unexpected_audit("
                    + "id BIGINT PRIMARY KEY, first_id BIGINT NOT NULL, detail VARCHAR(32), "
                    + "FOREIGN KEY(first_id) REFERENCES first_table(id))");
            jdbcTemplate.update("INSERT INTO first_table(id) VALUES (1)");
            jdbcTemplate.update("INSERT INTO second_table(id) VALUES (1)");
            jdbcTemplate.update(
                    "INSERT INTO unexpected_audit(id, first_id, detail) VALUES (1, 1, 'outside-yaml')");

            DataSetLoader.cleanTables(jdbcTemplate);

            assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM first_table", Integer.class));
            assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM second_table", Integer.class));
            assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM unexpected_audit", Integer.class));
        } finally {
            dataSource.destroy();
        }
    }

    @Test
    void retriesInitializationAfterFailure() {
        SmartTestRoutingDataSource dataSource = new SmartTestRoutingDataSource(URL);
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            assertThrows(RuntimeException.class, () -> SchemaInitializer.initialize(
                    jdbcTemplate, "classpath:sql/schema-invalid.sql"));

            SchemaInitializer.initialize(jdbcTemplate, "classpath:sql/schema-retry.sql");

            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = 'RETRY_TABLE'",
                    Integer.class));
        } finally {
            dataSource.destroy();
        }
    }

    @Test
    void isolatesRepeatedConcurrentCaseDataAcrossWorkerThreads() throws Exception {
        SmartTestRoutingDataSource dataSource = new SmartTestRoutingDataSource(URL);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            for (int round = 0; round < 3; round++) {
                CyclicBarrier barrier = new CyclicBarrier(4);
                List<Future<String>> futures = new ArrayList<>();
                for (int caseIndex = 0; caseIndex < 4; caseIndex++) {
                    final String marker = "round-" + round + "-case-" + caseIndex;
                    futures.add(executor.submit(() -> {
                        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
                        SchemaInitializer.initialize(jdbcTemplate, "classpath:sql/schema-concurrent.sql");
                        DataSetLoader.cleanTables(jdbcTemplate);
                        jdbcTemplate.update(
                                "INSERT INTO concurrent_record(id, marker) VALUES (1, ?)", marker);
                        barrier.await(10, TimeUnit.SECONDS);
                        assertEquals(1, jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM concurrent_record", Integer.class));
                        assertEquals(marker, jdbcTemplate.queryForObject(
                                "SELECT marker FROM concurrent_record WHERE id = 1", String.class));
                        return dataSource.currentDbKey();
                    }));
                }
                Set<String> databaseKeys = new HashSet<>();
                for (Future<String> future : futures) {
                    databaseKeys.add(future.get(15, TimeUnit.SECONDS));
                }
                assertEquals(4, databaseKeys.size());
            }
        } finally {
            executor.shutdownNow();
            dataSource.destroy();
        }
    }
}

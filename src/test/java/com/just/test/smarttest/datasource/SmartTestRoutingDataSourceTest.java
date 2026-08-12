package com.just.test.smarttest.datasource;

import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.context.CaseExecutionContext;
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

    private static final String URL = "jdbc:h2:mem:{key};MODE=MySQL";

    @Test
    void isolatesDatabasesAcrossApplicationContexts() {
        SmartTestRoutingDataSource first = new SmartTestRoutingDataSource(URL);
        SmartTestRoutingDataSource second = new SmartTestRoutingDataSource(URL);
        try {
            bindCase("contexts");
            assertNotEquals(first.currentDbKey(), second.currentDbKey());
        } finally {
            CaseExecutionContext.clear();
            first.destroy();
            second.destroy();
        }
    }

    @Test
    void releasesMemoryDatabaseWhenContextIsDestroyed() {
        SmartTestRoutingDataSource dataSource = new SmartTestRoutingDataSource(URL);
        bindCase("release");
        String databaseKey = dataSource.currentDbKey();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE released_table(id INT PRIMARY KEY)");

        dataSource.destroy();
        CaseExecutionContext.clear();

        JdbcTemplate reopened = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:h2:mem:" + databaseKey + ";MODE=MySQL", "sa", ""));
        assertEquals(0, reopened.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = 'RELEASED_TABLE'",
                Integer.class));
    }

    @Test
    void initializesAllSchemaResourcesAndCleansEveryTable() {
        SmartTestRoutingDataSource dataSource = new SmartTestRoutingDataSource(URL);
        try {
            bindCase("clean");
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
            CaseExecutionContext.clear();
            dataSource.destroy();
        }
    }

    @Test
    void retriesInitializationAfterFailure() {
        SmartTestRoutingDataSource dataSource = new SmartTestRoutingDataSource(URL);
        try {
            bindCase("retry");
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            assertThrows(RuntimeException.class, () -> SchemaInitializer.initialize(
                    jdbcTemplate, "classpath:sql/schema-invalid.sql"));

            SchemaInitializer.initialize(jdbcTemplate, "classpath:sql/schema-retry.sql");

            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = 'RETRY_TABLE'",
                    Integer.class));
        } finally {
            CaseExecutionContext.clear();
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
                        bindCase(marker);
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
                        try {
                            return dataSource.currentDbKey();
                        } finally {
                            dataSource.releaseCurrentCase();
                            CaseExecutionContext.clear();
                        }
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

    @Test
    void rejectsDatabaseAccessOutsideSmartTestCase() {
        SmartTestRoutingDataSource dataSource = new SmartTestRoutingDataSource(URL);
        try {
            IllegalStateException failure = assertThrows(IllegalStateException.class, dataSource::getConnection);
            org.junit.jupiter.api.Assertions.assertTrue(failure.getMessage().contains("active SmartTest case"));
        } finally {
            dataSource.destroy();
        }
    }

    @Test
    void isolatesCasesReusingTheSameThread() {
        SmartTestRoutingDataSource dataSource = new SmartTestRoutingDataSource(URL);
        try {
            bindCase("first");
            String firstKey = dataSource.currentDbKey();
            new JdbcTemplate(dataSource).execute("CREATE TABLE first_case(id INT)");
            dataSource.releaseCurrentCase();
            CaseExecutionContext.clear();

            bindCase("second");
            String secondKey = dataSource.currentDbKey();
            assertNotEquals(firstKey, secondKey);
            assertEquals(0, new JdbcTemplate(dataSource).queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = 'FIRST_CASE'",
                    Integer.class));
        } finally {
            CaseExecutionContext.clear();
            dataSource.destroy();
        }
    }

    private static void bindCase(String name) {
        CaseExecutionContext.bind(new CaseContext(name, "test/" + name));
    }
}

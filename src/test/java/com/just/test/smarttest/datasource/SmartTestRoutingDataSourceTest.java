package com.just.test.smarttest.datasource;

import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.context.CaseExecutionContext;
import com.just.test.smarttest.loader.DataSetLoader;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void rejectsDatabaseAccessAfterContextIsDestroyed() {
        SmartTestRoutingDataSource dataSource = new SmartTestRoutingDataSource(URL);
        try {
            bindCase("destroyed");
            new JdbcTemplate(dataSource).execute("CREATE TABLE before_destroy(id INT)");

            dataSource.destroy();

            IllegalStateException failure = assertThrows(IllegalStateException.class, dataSource::getConnection);
            org.junit.jupiter.api.Assertions.assertTrue(failure.getMessage().contains("ApplicationContext was destroyed"));
        } finally {
            CaseExecutionContext.clear();
            dataSource.destroy();
        }
    }

    @Test
    void waitsForActiveCaseBeforeDestroyingContext() throws Exception {
        SmartTestRoutingDataSource dataSource = new SmartTestRoutingDataSource(URL);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            bindCase("active");
            dataSource.beginCase();
            new JdbcTemplate(dataSource).execute("CREATE TABLE active_case(id INT)");

            Future<?> destroy = executor.submit(dataSource::destroy);
            Thread.sleep(100);
            assertFalse(destroy.isDone(), "Context destruction must wait for the active case");

            dataSource.releaseCurrentCase();
            destroy.get(5, TimeUnit.SECONDS);
            assertThrows(IllegalStateException.class, dataSource::getConnection);
        } finally {
            CaseExecutionContext.clear();
            executor.shutdownNow();
            dataSource.destroy();
        }
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
    void clonesCachedSchemaWithoutSharingRowsBetweenCases() throws Exception {
        Path schemaFile = Files.createTempFile("smarttest-schema-cache-", ".sql");
        Files.write(schemaFile, "CREATE TABLE clone_cache_record (id BIGINT PRIMARY KEY);\n"
                .getBytes(StandardCharsets.UTF_8));
        String schemaLocation = schemaFile.toUri().toString();
        SmartTestRoutingDataSource dataSource = new SmartTestRoutingDataSource(URL);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        try {
            bindCase("clone-first");
            SchemaInitializer.initialize(jdbcTemplate, schemaLocation);
            jdbcTemplate.update("INSERT INTO clone_cache_record(id) VALUES (1)");
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM clone_cache_record", Integer.class));
            dataSource.releaseCurrentCase();
            CaseExecutionContext.clear();
            Files.delete(schemaFile);

            bindCase("clone-second");
            SchemaInitializer.initialize(jdbcTemplate, schemaLocation);

            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM clone_cache_record", Integer.class));
            assertEquals(2, dataSource.schemaCloneCountForTests());
        } finally {
            Files.deleteIfExists(schemaFile);
            CaseExecutionContext.clear();
            dataSource.destroy();
        }
    }

    @Test
    void fallsBackToCachedDdlWhenSchemaCloneFails() {
        SmartTestRoutingDataSource dataSource = new SmartTestRoutingDataSource(URL);
        try {
            bindCase("clone-fallback");
            dataSource.failNextSchemaCloneForTests();

            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            SchemaInitializer.initialize(jdbcTemplate, "classpath:sql/schema-part-one.sql");

            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                            + "WHERE UPPER(TABLE_NAME) = 'FIRST_TABLE'",
                    Integer.class));
            assertEquals(0, dataSource.schemaCloneCountForTests());
            assertEquals(1, dataSource.schemaFallbackCountForTests());
        } finally {
            CaseExecutionContext.clear();
            dataSource.destroy();
        }
    }

    @Test
    void canDisableSchemaCloneWithSystemProperty() {
        SmartTestRoutingDataSource dataSource = new SmartTestRoutingDataSource(URL);
        String original = System.getProperty("smarttest.schema.clone");
        try {
            System.setProperty("smarttest.schema.clone", "false");
            bindCase("clone-disabled");
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

            SchemaInitializer.initialize(jdbcTemplate, "classpath:sql/schema-part-one.sql");

            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                            + "WHERE UPPER(TABLE_NAME) = 'FIRST_TABLE'",
                    Integer.class));
            assertEquals(0, dataSource.schemaCloneCountForTests());
            assertEquals(0, dataSource.schemaFallbackCountForTests());
        } finally {
            if (original == null) {
                System.clearProperty("smarttest.schema.clone");
            } else {
                System.setProperty("smarttest.schema.clone", original);
            }
            CaseExecutionContext.clear();
            dataSource.destroy();
        }
    }

    @Test
    void removesTableAutoIncrementOptionsButPreservesColumnAttribute() {
        String ddl = "CREATE TABLE t_xxx (id INT AUTO_INCREMENT PRIMARY KEY)"
                + " ENGINE=InnoDB AUTO_INCREMENT=100 auto_increment = 200 AUTO_INCREMENT  =   300"
                + " DEFAULT CHARSET=utf8mb4;";

        String cleaned = SchemaInitializer.cleanMySqlSyntax(ddl);

        assertTrue(cleaned.contains("id INT AUTO_INCREMENT PRIMARY KEY"));
        assertFalse(cleaned.matches("(?is).*AUTO_INCREMENT\\s*=\\s*\\d+.*"));
    }

    @Test
    void retriesInitializationAfterFailure() {
        SmartTestRoutingDataSource dataSource = new SmartTestRoutingDataSource(URL);
        try {
            bindCase("retry");
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            assertThrows(RuntimeException.class, () -> SchemaInitializer.initialize(
                    jdbcTemplate, "classpath:sql/schema-invalid.sql"));

            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = 'RETRY_TABLE'",
                    Integer.class));
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

    @Test
    void rebuildsClosedCaseDataSource() throws Exception {
        SmartTestRoutingDataSource dataSource = new SmartTestRoutingDataSource(URL);
        try {
            bindCase("closed");
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.execute("CREATE TABLE before_close(id INT)");
            String databaseKey = dataSource.currentDbKey();
            SingleConnectionDataSource closedDataSource = getCaseDataSource(dataSource, databaseKey);

            closedDataSource.destroy();

            jdbcTemplate.execute("CREATE TABLE after_rebuild(id INT)");
            SingleConnectionDataSource rebuiltDataSource = getCaseDataSource(dataSource, databaseKey);
            assertNotSame(closedDataSource, rebuiltDataSource);
            try (Connection connection = rebuiltDataSource.getConnection()) {
                assertTrue(connection.isValid(1));
            }
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = 'BEFORE_CLOSE'",
                    Integer.class));
        } finally {
            CaseExecutionContext.clear();
            dataSource.destroy();
        }
    }

    @SuppressWarnings("unchecked")
    private static SingleConnectionDataSource getCaseDataSource(
            SmartTestRoutingDataSource dataSource, String databaseKey) throws Exception {
        Field field = SmartTestRoutingDataSource.class.getDeclaredField("dataSources");
        field.setAccessible(true);
        Map<String, SingleConnectionDataSource> dataSources =
                (Map<String, SingleConnectionDataSource>) field.get(dataSource);
        return dataSources.get(databaseKey);
    }

    private static void bindCase(String name) {
        CaseExecutionContext.bind(new CaseContext(name, "test/" + name));
    }
}

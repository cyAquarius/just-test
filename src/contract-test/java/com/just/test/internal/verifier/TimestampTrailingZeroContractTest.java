package com.just.test.internal.verifier;

import com.just.test.internal.loader.DataSetLoader;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimestampTrailingZeroContractTest {

    private static final String CASE_PATH = "com/just/test/verifier/timestamp-trailing-zero";

    @Test
    void timestampTrailingZeroAgreesOnDatabaseAndResultPaths() {
        assertYamlTimestampString(DataSetLoader.parseYamlByPath(CASE_PATH, "response.yaml"), "createdAt");
        Map<String, Object> expect = DataSetLoader.parseYamlByPath(CASE_PATH, "expect.yaml");
        Object rows = expect.get("event");
        assertTrue(rows instanceof List, String.valueOf(rows));
        Object row = ((List<?>) rows).get(0);
        assertTrue(row instanceof Map, String.valueOf(row));
        assertYamlTimestampString((Map<?, ?>) row, "created_at");

        Timestamp onSecond = Timestamp.valueOf("2026-11-13 10:00:00");
        assertEquals("2026-11-13 10:00:00.0", onSecond.toString());

        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:verifier_timestamp_zero;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1", "sa", "", true);
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.execute("CREATE TABLE event(id INT, created_at TIMESTAMP)");
            jdbcTemplate.update("INSERT INTO event(id, created_at) VALUES (?, ?)", 1, onSecond);
            Object stored = jdbcTemplate.queryForObject(
                    "SELECT created_at FROM event WHERE id = 1", Object.class);
            assertTrue(stored instanceof Timestamp, String.valueOf(stored));
            assertEquals("2026-11-13 10:00:00.0", stored.toString());

            List<String> databaseFailures = DataSetVerifier.verify(jdbcTemplate, CASE_PATH);
            assertTrue(databaseFailures.isEmpty(), databaseFailures.toString());

            Map<String, Object> actual = new LinkedHashMap<>();
            actual.put("createdAt", onSecond);
            List<String> resultFailures = ResultVerifier.verify(actual, CASE_PATH);
            assertTrue(resultFailures.isEmpty(), resultFailures.toString());

            Timestamp fraction = Timestamp.valueOf("2026-11-13 10:00:00.1");
            assertEquals("2026-11-13 10:00:00.1", fraction.toString());
            jdbcTemplate.update("UPDATE event SET created_at = ? WHERE id = 1", fraction);
            List<String> databaseMiss = DataSetVerifier.verify(jdbcTemplate, CASE_PATH);
            assertTrue(databaseMiss.stream().anyMatch(failure ->
                    failure.contains("created_at")
                            && failure.contains("<2026-11-13 10:00:00>")
                            && failure.contains("<2026-11-13 10:00:00.1>")), databaseMiss.toString());

            actual.put("createdAt", fraction);
            List<String> resultMiss = ResultVerifier.verify(actual, CASE_PATH);
            assertTrue(resultMiss.stream().anyMatch(failure ->
                    failure.contains("createdAt")
                            && failure.contains("<2026-11-13 10:00:00>")
                            && failure.contains("<2026-11-13 10:00:00.1>")), resultMiss.toString());

            actual.put("createdAt", "2026-11-13 10:00:00.0");
            List<String> stringMiss = ResultVerifier.verify(actual, CASE_PATH);
            assertTrue(stringMiss.stream().anyMatch(failure ->
                    failure.contains("<2026-11-13 10:00:00>")
                            && failure.contains("<2026-11-13 10:00:00.0>")), stringMiss.toString());
        } finally {
            dataSource.destroy();
        }
    }

    private static void assertYamlTimestampString(Map<?, ?> yaml, String key) {
        Object value = yaml.get(key);
        assertEquals("2026-11-13 10:00:00", value);
        assertEquals(String.class, value.getClass(), key);
    }
}

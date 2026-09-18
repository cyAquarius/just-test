package com.just.test.smarttest.internal.verifier;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataSetVerifierTest {

    private static final String FLAG_CASE_PATH =
            "com/just/test/smarttest/verifier/database/flags";

    @Test
    void supportsNullConditionValues() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:verifier_null;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "", true);
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.execute("CREATE TABLE nullable_sample(id INT, marker VARCHAR(20))");
            jdbcTemplate.update("INSERT INTO nullable_sample(id, marker) VALUES (?, ?)", 1, null);

            Map<String, Object> expectedRow = new LinkedHashMap<>();
            expectedRow.put("marker[C]", null);
            expectedRow.put("id", 1);
            Map<String, Object> expected = new LinkedHashMap<>();
            expected.put("nullable_sample", Arrays.asList(expectedRow));

            List<String> failures = DataSetVerifier.verifyFromMap(jdbcTemplate, expected);

            assertTrue(failures.isEmpty(), failures.toString());
        } finally {
            dataSource.destroy();
        }
    }

    @Test
    void loadsYamlAndSupportsConditionNotExistAndIgnoredFields() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:verifier_flags;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "", true);
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.execute("CREATE TABLE sample(id INT, status VARCHAR(20), ignored VARCHAR(20))");
            jdbcTemplate.update("INSERT INTO sample(id, status, ignored) VALUES (?, ?, ?)",
                    1, "ready", "actual-value");

            List<String> failures = DataSetVerifier.verify(jdbcTemplate, FLAG_CASE_PATH);

            assertTrue(failures.isEmpty(), failures.toString());
        } finally {
            dataSource.destroy();
        }
    }

    @Test
    void rejectsUnsafeWhereColumnNames() {
        Map<String, Object> expectedRow = new LinkedHashMap<>();
        expectedRow.put("id;drop[C]", 1);
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("sample", Arrays.asList(expectedRow));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DataSetVerifier.verifyFromMap(new JdbcTemplate(), expected));
        assertTrue(error.getMessage().contains("column name"), error.getMessage());
    }
}

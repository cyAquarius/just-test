package com.just.test.internal.verifier;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataSetVerifierTest {

    private static final String FLAG_CASE_PATH =
            "com/just/test/verifier/database/flags";
    private static final String EXPECT_FLAGS = "C, CN, N, R, A, D, D<digits>, J";

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
    void rejectsLowercaseCnInsteadOfExactMatch() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:verifier_flag_cn;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "", true);
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.execute("CREATE TABLE sample(id INT, status VARCHAR(20))");
            jdbcTemplate.update("INSERT INTO sample(id, status) VALUES (?, ?)", 1, "ready");

            String casePath = "com/just/test/verifier/database/flag-cn";
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> DataSetVerifier.verify(jdbcTemplate, casePath));
            assertUnknownFlag(error, "id", "cn", casePath + "/expect.yaml", EXPECT_FLAGS);
        } finally {
            dataSource.destroy();
        }
    }

    @Test
    void rejectsMisspelledAndWrongStageExpectFlags() {
        assertUnknownExpect("com/just/test/verifier/database/flag-cc", "status", "CC");
        assertUnknownExpect("com/just/test/verifier/database/flag-nn", "ignored", "NN");
        assertUnknownExpect("com/just/test/verifier/database/flag-f", "gmt_create", "F");
    }

    @Test
    void keepsLegalMatcherFlags() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:verifier_legal_flags;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "", true);
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.execute("CREATE TABLE sample("
                    + "id INT, name VARCHAR(20), owner VARCHAR(20), ext VARCHAR(100), gmt TIMESTAMP)");
            jdbcTemplate.update(
                    "INSERT INTO sample(id, name, owner, ext, gmt) VALUES (?, ?, ?, ?, ?)",
                    1, "ready", "alice", "{\"k\":\"v\"}", new Timestamp(System.currentTimeMillis()));

            Map<String, Object> expectedRow = new LinkedHashMap<>();
            expectedRow.put("id[C]", 1);
            expectedRow.put("name[R]", "rea.*");
            expectedRow.put("owner[A]", null);
            expectedRow.put("ext[J]", "{\"k\":\"v\"}");
            expectedRow.put("gmt[D60]", "1999-01-01 00:00:00");
            Map<String, Object> expected = new LinkedHashMap<>();
            expected.put("sample", Arrays.asList(expectedRow));

            List<String> failures = DataSetVerifier.verifyFromMap(jdbcTemplate, expected);

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

    private static void assertUnknownExpect(String casePath, String field, String flag) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DataSetVerifier.verify(new JdbcTemplate(), casePath));
        assertUnknownFlag(error, field, flag, casePath + "/expect.yaml", EXPECT_FLAGS);
    }

    private static void assertUnknownFlag(IllegalArgumentException error, String field, String flag,
                                          String casePath, String legalFlags) {
        String message = error.getMessage();
        assertTrue(message.contains("field '" + field + "'"), message);
        assertTrue(message.contains("[" + flag + "]"), message);
        assertTrue(message.contains(casePath), message);
        assertTrue(message.contains("case-sensitive"), message);
        assertTrue(message.contains("Legal flags: " + legalFlags), message);
    }
}

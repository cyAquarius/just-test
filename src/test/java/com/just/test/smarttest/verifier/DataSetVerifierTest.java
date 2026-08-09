package com.just.test.smarttest.verifier;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DataSetVerifierTest {

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
}

package com.just.test.smarttest.internal.loader;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DataSetLoaderTest {

    @Test
    void appliesPrepareOnlyFlagsWithoutTreatingThemAsColumnNames() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:loader_flags;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "", true);
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.execute("CREATE TABLE loader_sample("
                    + "id INT, ignored VARCHAR(20), created_at TIMESTAMP)");

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", 1);
            row.put("ignored[N]", "must-not-be-inserted");
            row.put("created_at[F]", "CURRENT_TIMESTAMP");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("loader_sample", Arrays.asList(row));

            DataSetLoader.loadFromMap(jdbcTemplate, data, "test");

            Map<String, Object> actual = jdbcTemplate.queryForMap("SELECT * FROM loader_sample");
            assertEquals(1, actual.get("ID"));
            assertNull(actual.get("IGNORED"));
            assertNotNull(actual.get("CREATED_AT"));
        } finally {
            dataSource.destroy();
        }
    }
}

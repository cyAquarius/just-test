package com.just.test.internal.h2;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class H2FunctionRegistrarTest {

    @Test
    void replacesExistingAliasAndIsIdempotent() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:h2_function_registrar_test;MODE=MySQL");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setSuppressClose(true);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        try {
            jdbcTemplate.execute("CREATE ALIAS FIND_IN_SET FOR \"java.lang.Math.abs(int)\"");

            H2FunctionRegistrar.register(jdbcTemplate);
            assertEquals(2, jdbcTemplate.queryForObject(
                    "SELECT FIND_IN_SET('b', 'a,b,c')", Integer.class));

            H2FunctionRegistrar.register(jdbcTemplate);
            assertEquals(2, jdbcTemplate.queryForObject(
                    "SELECT FIND_IN_SET('b', 'a,b,c')", Integer.class));
        } finally {
            dataSource.destroy();
        }
    }

    @Test
    void failsFastWhenRegistrationFails() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate() {
            @Override
            public void execute(String sql) {
                throw new BadSqlGrammarException(
                        "register", sql, new SQLException("boom", "42000", 42000));
            }
        };

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> H2FunctionRegistrar.register(jdbcTemplate));

        assertTrue(failure.getMessage().contains("FIND_IN_SET"));
        assertTrue(failure.getMessage().contains("boom"));
        assertTrue(failure.getCause() instanceof BadSqlGrammarException);
    }
}

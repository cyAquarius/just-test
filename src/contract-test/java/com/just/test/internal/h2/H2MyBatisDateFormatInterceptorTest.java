package com.just.test.internal.h2;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class H2MyBatisDateFormatInterceptorTest {

    private static final String URL = "jdbc:h2:mem:mybatis_date_format_"
            + UUID.randomUUID().toString().replace("-", "")
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1";

    @Test
    void rewritesDateFormatThroughMyBatisStatementHandler() {
        DataSource dataSource = new DriverManagerDataSource(URL, "sa", "");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE date_format_record ("
                + "id BIGINT PRIMARY KEY, created_at TIMESTAMP NOT NULL)");
        jdbcTemplate.update("INSERT INTO date_format_record(id, created_at) VALUES (?, ?)",
                1L, Timestamp.valueOf("2024-03-05 12:30:00"));

        Configuration configuration = new Configuration(new Environment(
                "test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(DateFormatMapper.class);
        SqlSessionFactory sqlSessionFactory = new DefaultSqlSessionFactory(configuration);
        new JustTestMyBatisInterceptorConfigurer()
                .postProcessAfterInitialization(sqlSessionFactory, "sqlSessionFactory");

        assertTrue(configuration.getInterceptors().stream()
                .anyMatch(interceptor -> interceptor instanceof H2MySqlIfInterceptor));
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            DateFormatMapper mapper = sqlSession.getMapper(DateFormatMapper.class);
            assertEquals("2024-03-05", mapper.formatCreatedAt(1L));
        }
    }

    interface DateFormatMapper {

        @Select("SELECT DATE_FORMAT(created_at, '%Y-%m-%d') "
                + "FROM date_format_record WHERE id = #{id}")
        String formatCreatedAt(@Param("id") long id);
    }
}

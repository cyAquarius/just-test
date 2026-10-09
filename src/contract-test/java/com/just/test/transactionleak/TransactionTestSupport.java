package com.just.test.transactionleak;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

public final class TransactionTestSupport {

    private TransactionTestSupport() {
    }

    public static class ExpectedBusinessException extends RuntimeException {
        public ExpectedBusinessException(String message) {
            super(message);
        }
    }

    public interface RecordMapper {
        @Insert("INSERT INTO parallel_case_record(id, marker) VALUES (1, #{marker})")
        void insert(@Param("marker") String marker);

        @Select("SELECT marker FROM parallel_case_record WHERE id = 1")
        String findMarker();
    }

    public static class RecordService {
        private final RecordMapper mapper;

        RecordService(RecordMapper mapper) {
            this.mapper = mapper;
        }

        @Transactional
        public String insertAndFind(String marker) {
            mapper.insert(marker);
            return mapper.findMarker();
        }
    }

    @Configuration
    @EnableTransactionManagement
    public static class TestConfiguration {
        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
            bean.setDataSource(dataSource);
            SqlSessionFactory factory = bean.getObject();
            factory.getConfiguration().addMapper(RecordMapper.class);
            return factory;
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory);
        }

        @Bean
        RecordMapper recordMapper(SqlSessionTemplate template) {
            return template.getMapper(RecordMapper.class);
        }

        @Bean
        RecordService recordService(RecordMapper mapper) {
            return new RecordService(mapper);
        }
    }
}

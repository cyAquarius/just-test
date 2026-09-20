package com.just.test.mock.mybatismapper;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

@JustTest
@ContextConfiguration(classes = JustMockMyBatisMapperJustTest.TestConfiguration.class)
class JustMockMyBatisMapperJustTest implements JustTestLifecycle {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MapperConsumer mapperConsumer;

    @JustMock
    private SampleMapper mapper;

    @CaseSource
    void replacesMapperFactoryBeanWithoutRegisteringDuplicate(CaseContext context) {
        String[] logicalMapperNames = Arrays.stream(
                        applicationContext.getBeanNamesForType(SampleMapper.class))
                .filter(name -> !name.startsWith("scopedTarget."))
                .toArray(String[]::new);

        assertArrayEquals(new String[]{"sampleMapper"}, logicalMapperNames);
        assertFalse(Arrays.stream(applicationContext.getBeanDefinitionNames())
                .anyMatch(name -> name.endsWith("#0")));
        assertSame(applicationContext.getBean("sampleMapper"), mapperConsumer.getMapper());

        when(mapper.value()).thenReturn("mocked");
        assertEquals("mocked", mapperConsumer.call());
    }

    interface SampleMapper {
        String value();
    }

    static class MapperConsumer {
        private final SampleMapper mapper;

        MapperConsumer(SampleMapper mapper) {
            this.mapper = mapper;
        }

        SampleMapper getMapper() {
            return mapper;
        }

        String call() {
            return mapper.value();
        }
    }

    @Configuration
    static class TestConfiguration {
        @Bean
        MapperFactoryBean<SampleMapper> sampleMapper() {
            MapperFactoryBean<SampleMapper> factoryBean = new MapperFactoryBean<>();
            factoryBean.setMapperInterface(SampleMapper.class);
            return factoryBean;
        }

        @Bean
        MapperConsumer mapperConsumer(SampleMapper sampleMapper) {
            return new MapperConsumer(sampleMapper);
        }
    }
}

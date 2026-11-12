package com.just.test.demo.mapperintercept.autounion;

import com.just.test.annotation.JustTestProject;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import javax.sql.DataSource;

@JustTestProject(basePackages = "com.just.test.demo.mapperintercept.autounion")
@MapperScan(basePackageClasses = SampleMapper.class)
public class JustTestApplication {
    @Bean
    SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        return factory.getObject();
    }

    @Bean
    @Lazy
    UnrelatedFactory unrelatedFactory() {
        throw new AssertionError("Unrelated FactoryBean must not be initialized");
    }

    public static class UnrelatedFactory implements FactoryBean<Object> {
        public Object getObject() { throw new AssertionError("Unrelated product"); }
        public Class<?> getObjectType() { return null; }
    }

    // Reproduce older MyBatis definitions even on current Spring/MyBatis versions.
    @Bean
    static BeanDefinitionRegistryPostProcessor legacyMapperMetadata() {
        return new BeanDefinitionRegistryPostProcessor() {
            public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) { }
            public void postProcessBeanFactory(ConfigurableListableBeanFactory factory) {
                BeanDefinition mapper = factory.getBeanDefinition("sampleMapper");
                mapper.removeAttribute("factoryBeanObjectType");
            }
        };
    }
}

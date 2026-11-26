package com.just.test.demo.project.ordering;

import com.just.test.annotation.JustTestProject;
import com.just.test.demo.project.fixtures.persist.mapper.ProjectRecordMapper;
import com.just.test.internal.config.JustTestDataSourceConfig;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.MapPropertySource;

import javax.sql.DataSource;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.Collections;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectScanOrderingTest {
    static final String SCAN_PACKAGE = "com.just.test.demo.project.ordering.fixtures";

    @Test
    void scannedBeanMethodsBackOffAutoConfigurationLikeHandwrittenBootstrap() {
        assertScannedBeans(Handwritten.class);
        assertScannedBeans(Project.class);
    }

    @Test
    void myBatisAutoConfigurationBindsPropertiesToTheFrameworkDataSource() throws Exception {
        URL artifact = Paths.get("target", "test-fixtures", "mybatis-autoconfigure.jar").toUri().toURL();
        try (URLClassLoader loader = new URLClassLoader(new URL[] {artifact}, getClass().getClassLoader());
             AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.setClassLoader(loader);
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "myBatisContract", Collections.singletonMap(
                            "mybatis.configuration.map-underscore-to-camel-case", "true")));
            context.register(JustTestDataSourceConfig.class, AutoConfiguredProject.class);
            context.refresh();

            SqlSessionFactory factory = context.getBean(SqlSessionFactory.class);
            assertTrue(factory.getConfiguration().isMapUnderscoreToCamelCase());
            assertSame(context.getBean("justTestDataSource", DataSource.class),
                    factory.getConfiguration().getEnvironment().getDataSource());
            assertTrue(factory.getConfiguration().hasMapper(ProjectRecordMapper.class));
        }
    }

    private void assertScannedBeans(Class<?> bootstrap) {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.setAllowBeanDefinitionOverriding(false);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(beans)) {
            context.register(JustTestDataSourceConfig.class, bootstrap);
            context.refresh();
            assertEquals(7, context.getBean(TransactionTemplate.class).getTimeout());
            assertTrue(context.getBean(SqlSessionFactory.class).getConfiguration().isMapUnderscoreToCamelCase());
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(SCAN_PACKAGE)
    static class Handwritten {
    }

    @JustTestProject(basePackages = SCAN_PACKAGE,
            mapperPackages = "com.just.test.demo.project.fixtures.persist.mapper")
    static class Project {
    }

    @JustTestProject(basePackages = "com.just.test.demo.project.fixtures.persist",
            mapperPackages = "com.just.test.demo.project.fixtures.persist.mapper")
    static class AutoConfiguredProject {
    }
}

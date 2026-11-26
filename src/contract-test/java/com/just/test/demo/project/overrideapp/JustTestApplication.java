package com.just.test.demo.project.overrideapp;

import com.just.test.annotation.JustTestProject;
import com.just.test.demo.project.fixtures.scan.ExtraService;
import com.just.test.demo.project.fixtures.scan.PlainCollaborator;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;

/** 在框架默认 denylist 上叠加项目差异：excludeFilters 排除 ExtraService、includeFilters 纳入无刻板类型。 */
@JustTestProject(
        basePackages = "com.just.test.demo.project.fixtures.scan",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.CUSTOM,
                classes = JustTestApplication.ExcludeExtra.class
        ),
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = PlainCollaborator.class
        )
)
public class JustTestApplication {
    public static class ExcludeExtra implements TypeFilter, EnvironmentAware {

        private final ClassLoader classLoader;
        private Environment environment;

        public ExcludeExtra(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        public void setEnvironment(Environment environment) {
            this.environment = environment;
        }

        @Override
        public boolean match(MetadataReader reader, MetadataReaderFactory factory) {
            return classLoader != null && environment != null
                    && ExtraService.class.getName().equals(reader.getClassMetadata().getClassName());
        }
    }
}

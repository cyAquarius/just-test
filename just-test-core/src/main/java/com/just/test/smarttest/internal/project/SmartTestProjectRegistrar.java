package com.just.test.smarttest.internal.project;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 处理 {@code @SmartTestProject}：默认扫描排除、可选 MyBatis 装配、
 * 默认及声明的数据源 / 事务管理器别名。
 *
 * <p>不对消费方提供兼容承诺。由 Boot 模块的 {@code @SmartTestProject} 通过
 * {@code @Import} 注册，因此必须保持 public。</p>
 */
public class SmartTestProjectRegistrar implements ImportBeanDefinitionRegistrar,
        EnvironmentAware, ResourceLoaderAware, BeanClassLoaderAware {

    static final String ANNOTATION_NAME = "com.just.test.smarttest.annotation.SmartTestProject";
    static final String MYBATIS_SPRING_FACTORY = "org.mybatis.spring.SqlSessionFactoryBean";

    private Environment environment;
    private ResourceLoader resourceLoader;
    private ClassLoader classLoader = ClassUtils.getDefaultClassLoader();

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                        BeanDefinitionRegistry registry) {
        AnnotationAttributes attributes = resolveAttributes(importingClassMetadata);
        String[] basePackages = requiredBasePackages(attributes);
        String[] mapperPackages = trimAll(attributes.getStringArray("mapperPackages"));
        requireMyBatisSpringIfNeeded(mapperPackages, classLoader);

        ClassPathBeanDefinitionScanner scanner = createScanner(registry);
        scanner.addExcludeFilter(new SmartTestProjectTypeExcludeFilter(
                importingClassMetadata.getClassName(),
                attributes.getClassArray("excludeClasses"),
                classLoader));
        SmartTestProjectScanFilters.apply(scanner, attributes, environment, resourceLoader, registry, classLoader);
        scanner.scan(basePackages);

        if (mapperPackages.length > 0) {
            SmartTestProjectMyBatisSupport.register(registry, mapperPackages);
        }
        SmartTestProjectDataSourceAliasPostProcessor.register(
                registry,
                trimAll(attributes.getStringArray("dataSourceAliases")),
                trimAll(attributes.getStringArray("transactionManagerAliases")));
    }

    static AnnotationAttributes resolveAttributes(AnnotationMetadata metadata) {
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                metadata.getAnnotationAttributes(ANNOTATION_NAME, false));
        if (attributes == null) {
            throw new IllegalStateException(
                    "[SmartTest] @SmartTestProject metadata is missing on " + metadata.getClassName());
        }
        return attributes;
    }

    static String[] requiredBasePackages(AnnotationAttributes attributes) {
        String[] basePackages = trimAll(attributes.getStringArray("basePackages"));
        if (basePackages.length == 0) {
            basePackages = trimAll(attributes.getStringArray("value"));
        }
        if (basePackages.length == 0) {
            throw new IllegalStateException(
                    "[SmartTest] @SmartTestProject requires basePackages (the application root to scan). "
                            + "SmartTest does not guess a business package; declare it explicitly, "
                            + "for example basePackages = \"com.example\".");
        }
        return basePackages;
    }

    static void requireMyBatisSpringIfNeeded(String[] mapperPackages, ClassLoader classLoader) {
        if (mapperPackages == null || mapperPackages.length == 0) {
            return;
        }
        if (!ClassUtils.isPresent(MYBATIS_SPRING_FACTORY, classLoader)) {
            throw new IllegalStateException(
                    "[SmartTest] @SmartTestProject mapperPackages requires mybatis-spring on the test "
                            + "classpath, but " + MYBATIS_SPRING_FACTORY + " was not found. "
                            + "Add mybatis-spring (or mybatis-spring-boot-starter) or omit mapperPackages.");
        }
    }

    static String[] trimAll(String[] values) {
        if (values == null || values.length == 0) {
            return new String[0];
        }
        List<String> trimmed = new ArrayList<String>(values.length);
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                trimmed.add(value.trim());
            }
        }
        return trimmed.toArray(new String[0]);
    }

    private ClassPathBeanDefinitionScanner createScanner(BeanDefinitionRegistry registry) {
        if (environment != null && resourceLoader != null) {
            return new ClassPathBeanDefinitionScanner(registry, true, environment, resourceLoader);
        }
        if (environment != null) {
            return new ClassPathBeanDefinitionScanner(registry, true, environment);
        }
        return new ClassPathBeanDefinitionScanner(registry, true);
    }
}

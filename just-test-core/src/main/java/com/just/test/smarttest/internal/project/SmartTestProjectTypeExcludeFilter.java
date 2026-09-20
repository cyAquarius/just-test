package com.just.test.smarttest.internal.project;

import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code @SmartTestProject} 默认组件扫描排除：生产启动类、Web/API 边界、
 * 可选的 Feign / 调度刻板类型，以及 {@code excludeClasses}。
 *
 * <p>不对消费方提供兼容承诺。按注解名 / 可选类型探测，不引入硬依赖。</p>
 */
public class SmartTestProjectTypeExcludeFilter implements TypeFilter {

    static final String SMART_TEST_PROJECT = SmartTestProjectRegistrar.ANNOTATION_NAME;
    static final String SPRING_BOOT_CONFIGURATION = "org.springframework.boot.SpringBootConfiguration";
    static final String SPRING_BOOT_APPLICATION =
            "org.springframework.boot.autoconfigure.SpringBootApplication";

    static final String[] STEREOTYPE_ANNOTATION_NAMES = {
            "org.springframework.stereotype.Controller",
            "org.springframework.web.bind.annotation.RestController",
            "org.springframework.web.bind.annotation.ControllerAdvice",
            "org.springframework.web.bind.annotation.RestControllerAdvice",
            "org.springframework.cloud.openfeign.FeignClient",
            "org.springframework.scheduling.annotation.EnableScheduling",
            "org.springframework.batch.core.configuration.annotation.EnableBatchProcessing",
            "com.xxl.job.core.handler.annotation.JobHandler",
            "org.apache.shardingsphere.elasticjob.annotation.ElasticJobConfiguration"
    };

    static final String[] ASSIGNABLE_TYPE_NAMES = {
            "org.quartz.Job",
            "org.springframework.scheduling.quartz.QuartzJobBean"
    };

    private final String importingClassName;
    private final Set<String> excludeClassNames;
    private final List<TypeFilter> stereotypeFilters;

    public SmartTestProjectTypeExcludeFilter(String importingClassName,
                                             Class<?>[] excludeClasses,
                                             ClassLoader classLoader) {
        this.importingClassName = importingClassName;
        this.excludeClassNames = classNames(excludeClasses);
        this.stereotypeFilters = createStereotypeFilters(classLoader);
    }

    @Override
    public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory)
            throws IOException {
        AnnotationMetadata metadata = metadataReader.getAnnotationMetadata();
        String className = metadata.getClassName();
        if (excludeClassNames.contains(className)) {
            return true;
        }
        if (isProductionBootstrap(metadata, className)) {
            return true;
        }
        for (TypeFilter filter : stereotypeFilters) {
            if (filter.match(metadataReader, metadataReaderFactory)) {
                return true;
            }
        }
        return false;
    }

    static List<TypeFilter> createStereotypeFilters(ClassLoader classLoader) {
        List<TypeFilter> filters = new ArrayList<TypeFilter>();
        for (String annotationName : STEREOTYPE_ANNOTATION_NAMES) {
            TypeFilter filter = optionalAnnotationFilter(annotationName, classLoader);
            if (filter != null) {
                filters.add(filter);
            }
        }
        for (String typeName : ASSIGNABLE_TYPE_NAMES) {
            TypeFilter filter = optionalAssignableFilter(typeName, classLoader);
            if (filter != null) {
                filters.add(filter);
            }
        }
        return Collections.unmodifiableList(filters);
    }

    static TypeFilter optionalAnnotationFilter(String annotationName, ClassLoader classLoader) {
        Class<? extends Annotation> annotationType = loadAnnotation(annotationName, classLoader);
        if (annotationType == null) {
            return null;
        }
        return new AnnotationTypeFilter(annotationType);
    }

    static TypeFilter optionalAssignableFilter(String typeName, ClassLoader classLoader) {
        Class<?> targetType = loadClass(typeName, classLoader);
        if (targetType == null) {
            return null;
        }
        return new AssignableTypeFilter(targetType);
    }

    private boolean isProductionBootstrap(AnnotationMetadata metadata, String className) {
        if (!metadata.isAnnotated(SPRING_BOOT_CONFIGURATION)
                && !metadata.isAnnotated(SPRING_BOOT_APPLICATION)) {
            return false;
        }
        if (className.equals(importingClassName) || metadata.isAnnotated(SMART_TEST_PROJECT)) {
            return false;
        }
        return true;
    }

    private static Set<String> classNames(Class<?>[] excludeClasses) {
        if (excludeClasses == null || excludeClasses.length == 0) {
            return Collections.emptySet();
        }
        Set<String> names = new LinkedHashSet<String>();
        for (Class<?> excludeClass : excludeClasses) {
            if (excludeClass != null) {
                names.add(excludeClass.getName());
            }
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Annotation> loadAnnotation(String className, ClassLoader classLoader) {
        Class<?> type = loadClass(className, classLoader);
        if (type == null || !type.isAnnotation()) {
            return null;
        }
        return (Class<? extends Annotation>) type;
    }

    private static Class<?> loadClass(String className, ClassLoader classLoader) {
        if (!ClassUtils.isPresent(className, classLoader)) {
            return null;
        }
        try {
            return ClassUtils.forName(className, classLoader);
        } catch (ClassNotFoundException ex) {
            return null;
        } catch (LinkageError ex) {
            return null;
        }
    }
}

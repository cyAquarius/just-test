package com.just.test.smarttest.internal.project;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AspectJTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.ClassUtils;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 将 {@code @SmartTestProject} 上的 include/exclude filters 应用到扫描器。
 *
 * <p>用户过滤器叠加在默认 denylist 之上，不替换框架默认排除。</p>
 */
final class SmartTestProjectScanFilters {

    private SmartTestProjectScanFilters() {
    }

    static void apply(ClassPathBeanDefinitionScanner scanner,
                      AnnotationAttributes attributes,
                      Environment environment,
                      ResourceLoader resourceLoader,
                      BeanDefinitionRegistry registry,
                      ClassLoader classLoader) {
        applyFilters(scanner, attributes.get("includeFilters"), false,
                environment, resourceLoader, registry, classLoader);
        applyFilters(scanner, attributes.get("excludeFilters"), true,
                environment, resourceLoader, registry, classLoader);
    }

    @SuppressWarnings("unchecked")
    private static void applyFilters(ClassPathBeanDefinitionScanner scanner,
                                     Object rawFilters,
                                     boolean exclude,
                                     Environment environment,
                                     ResourceLoader resourceLoader,
                                     BeanDefinitionRegistry registry,
                                     ClassLoader classLoader) {
        if (rawFilters == null) {
            return;
        }
        Object[] filters = rawFilters instanceof Object[] ? (Object[]) rawFilters : new Object[] {rawFilters};
        for (Object filter : filters) {
            if (filter == null) {
                continue;
            }
            AnnotationAttributes filterAttributes = filter instanceof AnnotationAttributes
                    ? (AnnotationAttributes) filter
                    : AnnotationAttributes.fromMap((Map<String, Object>) filter);
            if (filterAttributes == null) {
                continue;
            }
            addFilter(scanner, filterAttributes, exclude, environment, resourceLoader, registry, classLoader);
        }
    }

    private static void addFilter(ClassPathBeanDefinitionScanner scanner,
                                  AnnotationAttributes filterAttributes,
                                  boolean exclude,
                                  Environment environment,
                                  ResourceLoader resourceLoader,
                                  BeanDefinitionRegistry registry,
                                  ClassLoader classLoader) {
        FilterType filterType = filterAttributes.getEnum("type");
        Class<?>[] filterClasses = resolveFilterClasses(filterAttributes);
        String[] patterns = filterAttributes.getStringArray("pattern");
        if (filterType == FilterType.ANNOTATION || filterType == FilterType.ASSIGNABLE_TYPE
                || filterType == FilterType.CUSTOM) {
            for (Class<?> filterClass : filterClasses) {
                addClassFilter(scanner, filterType, filterClass, exclude,
                        environment, resourceLoader, registry);
            }
            return;
        }
        for (String pattern : patterns) {
            addPatternFilter(scanner, filterType, pattern, exclude, classLoader);
        }
    }

    private static Class<?>[] resolveFilterClasses(AnnotationAttributes filterAttributes) {
        Class<?>[] classes = filterAttributes.getClassArray("classes");
        if (classes.length > 0) {
            return classes;
        }
        return filterAttributes.getClassArray("value");
    }

    @SuppressWarnings("unchecked")
    private static void addClassFilter(ClassPathBeanDefinitionScanner scanner,
                                       FilterType filterType,
                                       Class<?> filterClass,
                                       boolean exclude,
                                       Environment environment,
                                       ResourceLoader resourceLoader,
                                       BeanDefinitionRegistry registry) {
        TypeFilter typeFilter;
        if (filterType == FilterType.ANNOTATION) {
            typeFilter = new AnnotationTypeFilter((Class<? extends Annotation>) filterClass);
        } else if (filterType == FilterType.ASSIGNABLE_TYPE) {
            typeFilter = new AssignableTypeFilter(filterClass);
        } else if (filterType == FilterType.CUSTOM) {
            if (!TypeFilter.class.isAssignableFrom(filterClass)) {
                throw new IllegalStateException(
                        "[SmartTest] @SmartTestProject custom filter must implement TypeFilter: "
                                + filterClass.getName());
            }
            typeFilter = instantiateCustomFilter(filterClass, environment, resourceLoader, registry);
        } else {
            throw new IllegalStateException(
                    "[SmartTest] @SmartTestProject filter type " + filterType
                            + " does not accept classes=" + filterClass.getName());
        }
        addFilter(scanner, typeFilter, exclude);
    }

    private static void addPatternFilter(ClassPathBeanDefinitionScanner scanner,
                                         FilterType filterType,
                                         String pattern,
                                         boolean exclude,
                                         ClassLoader classLoader) {
        TypeFilter typeFilter;
        if (filterType == FilterType.REGEX) {
            typeFilter = new RegexPatternTypeFilter(Pattern.compile(pattern));
        } else if (filterType == FilterType.ASPECTJ) {
            typeFilter = new AspectJTypeFilter(pattern, classLoader);
        } else {
            throw new IllegalStateException(
                    "[SmartTest] @SmartTestProject filter type " + filterType
                            + " does not accept pattern=" + pattern);
        }
        addFilter(scanner, typeFilter, exclude);
    }

    private static TypeFilter instantiateCustomFilter(Class<?> filterClass,
                                                      Environment environment,
                                                      ResourceLoader resourceLoader,
                                                      BeanDefinitionRegistry registry) {
        try {
            Class<?> strategyUtils = ClassUtils.forName(
                    "org.springframework.context.annotation.ParserStrategyUtils",
                    filterClass.getClassLoader());
            return (TypeFilter) strategyUtils.getMethod(
                    "instantiateClass",
                    Class.class, Class.class, Environment.class, ResourceLoader.class,
                    BeanDefinitionRegistry.class)
                    .invoke(null, filterClass, TypeFilter.class, environment, resourceLoader, registry);
        } catch (ReflectiveOperationException ex) {
            return (TypeFilter) BeanUtils.instantiateClass(filterClass);
        }
    }

    private static void addFilter(ClassPathBeanDefinitionScanner scanner, TypeFilter filter, boolean exclude) {
        if (exclude) {
            scanner.addExcludeFilter(filter);
        } else {
            scanner.addIncludeFilter(filter);
        }
    }
}

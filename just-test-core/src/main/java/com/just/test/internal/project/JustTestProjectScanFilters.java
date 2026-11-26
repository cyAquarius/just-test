package com.just.test.internal.project;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.TypeFilterUtils;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.TypeFilter;

import java.util.ArrayList;
import java.util.List;

final class JustTestProjectScanFilters {

    private JustTestProjectScanFilters() {
    }

    static List<TypeFilter> excludes(AnnotationAttributes attributes, Environment environment,
                                     ResourceLoader resourceLoader, BeanDefinitionRegistry registry) {
        List<TypeFilter> filters = new ArrayList<>();
        for (AnnotationAttributes filter : attributes.getAnnotationArray("excludeFilters")) {
            filters.addAll(TypeFilterUtils.createTypeFiltersFor(filter, environment, resourceLoader, registry));
        }
        return filters;
    }
}

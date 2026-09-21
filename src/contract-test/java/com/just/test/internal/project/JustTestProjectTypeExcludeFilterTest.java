package com.just.test.internal.project;

import com.just.test.demo.project.defaultapp.JustTestApplication;
import com.just.test.demo.project.fixtures.feign.DemoFeignClient;
import com.just.test.demo.project.fixtures.scan.DemoMarkerService;
import com.just.test.demo.project.fixtures.scan.DemoProductionApplication;
import com.just.test.demo.project.fixtures.scan.DemoSchedulingConfig;
import com.just.test.demo.project.fixtures.scan.DemoWebController;
import com.just.test.demo.project.fixtures.scan.ExtraService;
import org.junit.jupiter.api.Test;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JustTestProjectTypeExcludeFilterTest {

    private final SimpleMetadataReaderFactory metadataReaderFactory = new SimpleMetadataReaderFactory();
    private final ClassLoader classLoader = ClassUtils.getDefaultClassLoader();
    private final JustTestProjectTypeExcludeFilter filter = new JustTestProjectTypeExcludeFilter(
            JustTestApplication.class.getName(), new Class<?>[] {ExtraService.class}, classLoader);

    @Test
    void keepsOrdinaryServices() throws IOException {
        assertFalse(matches(DemoMarkerService.class));
    }

    @Test
    void excludesControllersWhenStereotypeIsPresent() throws IOException {
        assertTrue(matches(DemoWebController.class));
        assertNotNull(JustTestProjectTypeExcludeFilter.optionalAnnotationFilter(
                Controller.class.getName(), classLoader));
    }

    @Test
    void excludesProductionBootstrapExceptJustTestProject() throws IOException {
        assertTrue(matches(DemoProductionApplication.class));
        assertFalse(matches(JustTestApplication.class));
    }

    @Test
    void excludesSchedulingStereotypesWhenPresent() throws IOException {
        assertTrue(matches(DemoSchedulingConfig.class));
    }

    @Test
    void excludesDeclaredExcludeClasses() throws IOException {
        assertTrue(matches(ExtraService.class));
    }

    @Test
    void skipsOptionalStereotypesThatAreNotOnTheClasspath() {
        assertNull(JustTestProjectTypeExcludeFilter.optionalAnnotationFilter(
                "com.xxl.job.core.handler.annotation.JobHandler", classLoader));
        assertNull(JustTestProjectTypeExcludeFilter.optionalAssignableFilter(
                "org.quartz.Job", classLoader));
    }

    @Test
    void excludesFeignClientsWhenAnnotationIsPresent() throws IOException {
        assertNotNull(JustTestProjectTypeExcludeFilter.optionalAnnotationFilter(
                "org.springframework.cloud.openfeign.FeignClient", classLoader));
        assertTrue(matches(DemoFeignClient.class));
    }

    @Test
    void defaultDenylistIncludesFeignAndWebEdges() {
        assertTrue(containsName(
                JustTestProjectTypeExcludeFilter.STEREOTYPE_ANNOTATION_NAMES,
                "org.springframework.cloud.openfeign.FeignClient"));
        assertTrue(containsName(
                JustTestProjectTypeExcludeFilter.STEREOTYPE_ANNOTATION_NAMES,
                "org.springframework.web.bind.annotation.RestController"));
        assertTrue(containsName(
                JustTestProjectTypeExcludeFilter.STEREOTYPE_ANNOTATION_NAMES,
                "org.springframework.web.bind.annotation.ControllerAdvice"));
    }

    @Test
    void optionalAnnotationFilterUsesPresentTypes() {
        TypeFilter controllerFilter = JustTestProjectTypeExcludeFilter.optionalAnnotationFilter(
                Controller.class.getName(), classLoader);
        assertNotNull(controllerFilter);
    }

    private boolean matches(Class<?> type) throws IOException {
        MetadataReader reader = metadataReaderFactory.getMetadataReader(type.getName());
        return filter.match(reader, metadataReaderFactory);
    }

    private static boolean containsName(String[] names, String expected) {
        for (String name : names) {
            if (expected.equals(name)) {
                return true;
            }
        }
        return false;
    }
}

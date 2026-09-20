package com.just.test.smarttest.internal.project;

import com.just.test.smarttest.demo.project.defaultapp.SmartTestApplication;
import com.just.test.smarttest.demo.project.fixtures.scan.DemoMarkerService;
import com.just.test.smarttest.demo.project.fixtures.scan.DemoProductionApplication;
import com.just.test.smarttest.demo.project.fixtures.scan.DemoSchedulingConfig;
import com.just.test.smarttest.demo.project.fixtures.scan.DemoWebController;
import com.just.test.smarttest.demo.project.fixtures.scan.ExtraService;
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

class SmartTestProjectTypeExcludeFilterTest {

    private final SimpleMetadataReaderFactory metadataReaderFactory = new SimpleMetadataReaderFactory();
    private final ClassLoader classLoader = ClassUtils.getDefaultClassLoader();
    private final SmartTestProjectTypeExcludeFilter filter = new SmartTestProjectTypeExcludeFilter(
            SmartTestApplication.class.getName(), new Class<?>[] {ExtraService.class}, classLoader);

    @Test
    void keepsOrdinaryServices() throws IOException {
        assertFalse(matches(DemoMarkerService.class));
    }

    @Test
    void excludesControllersWhenStereotypeIsPresent() throws IOException {
        assertTrue(matches(DemoWebController.class));
        assertNotNull(SmartTestProjectTypeExcludeFilter.optionalAnnotationFilter(
                Controller.class.getName(), classLoader));
    }

    @Test
    void excludesProductionBootstrapExceptSmartTestProject() throws IOException {
        assertTrue(matches(DemoProductionApplication.class));
        assertFalse(matches(SmartTestApplication.class));
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
        assertNull(SmartTestProjectTypeExcludeFilter.optionalAnnotationFilter(
                "org.springframework.cloud.openfeign.FeignClient", classLoader));
        assertNull(SmartTestProjectTypeExcludeFilter.optionalAssignableFilter(
                "org.quartz.Job", classLoader));
    }

    @Test
    void defaultDenylistIncludesFeignAndWebEdges() {
        assertTrue(containsName(
                SmartTestProjectTypeExcludeFilter.STEREOTYPE_ANNOTATION_NAMES,
                "org.springframework.cloud.openfeign.FeignClient"));
        assertTrue(containsName(
                SmartTestProjectTypeExcludeFilter.STEREOTYPE_ANNOTATION_NAMES,
                "org.springframework.web.bind.annotation.RestController"));
        assertTrue(containsName(
                SmartTestProjectTypeExcludeFilter.STEREOTYPE_ANNOTATION_NAMES,
                "org.springframework.web.bind.annotation.ControllerAdvice"));
    }

    @Test
    void optionalAnnotationFilterUsesPresentTypes() {
        TypeFilter controllerFilter = SmartTestProjectTypeExcludeFilter.optionalAnnotationFilter(
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

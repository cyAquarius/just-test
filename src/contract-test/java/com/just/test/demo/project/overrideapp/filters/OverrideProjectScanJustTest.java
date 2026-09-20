package com.just.test.demo.project.overrideapp.filters;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.demo.project.fixtures.scan.DemoMarkerService;
import com.just.test.demo.project.fixtures.scan.DemoWebController;
import com.just.test.demo.project.fixtures.scan.ExtraService;
import com.just.test.demo.project.fixtures.scan.PlainCollaborator;
import com.just.test.lifecycle.JustTestLifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@JustTest
class OverrideProjectScanJustTest implements JustTestLifecycle {

    @Autowired
    private DemoMarkerService markerService;

    @Autowired
    private PlainCollaborator plainCollaborator;

    @Autowired
    private ApplicationContext applicationContext;

    @CaseSource
    void overlaysProjectFiltersOnDefaults(CaseContext context) {
        assertNotNull(markerService);
        assertNotNull(plainCollaborator);
        assertFalse(applicationContext.getBeanNamesForType(ExtraService.class).length > 0);
        assertFalse(applicationContext.getBeanNamesForType(DemoWebController.class).length > 0);
        context.setResult(plainCollaborator.id());
    }
}

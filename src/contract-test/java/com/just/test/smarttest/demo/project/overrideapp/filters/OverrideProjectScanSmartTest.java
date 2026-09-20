package com.just.test.smarttest.demo.project.overrideapp.filters;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.demo.project.fixtures.scan.DemoMarkerService;
import com.just.test.smarttest.demo.project.fixtures.scan.DemoWebController;
import com.just.test.smarttest.demo.project.fixtures.scan.ExtraService;
import com.just.test.smarttest.demo.project.fixtures.scan.PlainCollaborator;
import com.just.test.smarttest.lifecycle.SmartTestLifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SmartTest
class OverrideProjectScanSmartTest implements SmartTestLifecycle {

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

package com.just.test.smarttest.demo;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.lifecycle.SmartTestLifecycle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTestContextBootstrapper;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SmartTest
@BootstrapWith(SpringBootTestContextBootstrapper.class)
@ContextConfiguration(classes = SpringBootTestContextSmartTest.TestApplication.class)
class SpringBootTestContextSmartTest implements SmartTestLifecycle {

    @Value("${smarttest.boot.profile-marker:missing}")
    private String profileMarker;

    @CaseSource
    void loadsBootProfileConfigurationWithoutDuplicatingSmartTestLifecycle(CaseContext context) {
        assertEquals(context.getString("marker"), profileMarker);
    }

    @SpringBootConfiguration
    static class TestApplication {
    }
}

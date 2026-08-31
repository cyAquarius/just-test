package com.just.test.smarttest.demo.smarttest;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.lifecycle.SmartTestLifecycle;
import org.springframework.beans.factory.annotation.Value;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SmartTest
class SpringBootTestContextSmartTest implements SmartTestLifecycle {

    @Value("${smarttest.boot.profile-marker:missing}")
    private String profileMarker;

    @CaseSource
    void prefersDedicatedSmartTestApplicationOverParentProductionApplication(CaseContext context) {
        assertEquals(context.getString("marker"), profileMarker);
    }
}

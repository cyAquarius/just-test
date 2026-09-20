package com.just.test.demo.justtest;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import org.springframework.beans.factory.annotation.Value;

import static org.junit.jupiter.api.Assertions.assertEquals;

@JustTest
class SpringBootTestContextJustTest implements JustTestLifecycle {

    @Value("${justtest.boot.profile-marker:missing}")
    private String profileMarker;

    @CaseSource
    void prefersDedicatedJustTestApplicationOverParentProductionApplication(CaseContext context) {
        assertEquals(context.getString("marker"), profileMarker);
    }
}

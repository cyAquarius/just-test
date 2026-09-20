package com.just.test.smarttest.demo.project.okhttpapp.okhttp;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.annotation.SmartTestProject;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.demo.project.okhttpapp.SmartTestApplication;
import com.just.test.smarttest.lifecycle.SmartTestLifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SmartTest
class FeignOkHttpOptInSmartTest implements SmartTestLifecycle {

    @Autowired
    private Environment environment;

    @Autowired
    private ApplicationContext applicationContext;

    @CaseSource
    void doesNotForceFeignOkHttpOff(CaseContext context) {
        assertTrue(SmartTestApplication.class.getAnnotation(SmartTestProject.class).enableFeignOkHttp());
        assertTrue(environment instanceof ConfigurableEnvironment);
        assertFalse(((ConfigurableEnvironment) environment).getPropertySources()
                .contains("smartTestProjectFeignOkHttp"));
        assertNull(environment.getProperty("feign.okhttp.enabled"));
        assertNull(environment.getProperty("spring.cloud.openfeign.okhttp.enabled"));
        assertFalse(applicationContext.containsBean("client"));
        context.setResult("opt-in");
    }
}

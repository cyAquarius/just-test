package com.just.test.demo.project.okhttpapp.okhttp;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.annotation.JustTestProject;
import com.just.test.context.CaseContext;
import com.just.test.demo.project.okhttpapp.JustTestApplication;
import com.just.test.lifecycle.JustTestLifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JustTest
class FeignOkHttpOptInJustTest implements JustTestLifecycle {

    @Autowired
    private Environment environment;

    @Autowired
    private ApplicationContext applicationContext;

    @CaseSource
    void doesNotForceFeignOkHttpOff(CaseContext context) {
        assertTrue(JustTestApplication.class.getAnnotation(JustTestProject.class).enableFeignOkHttp());
        assertTrue(environment instanceof ConfigurableEnvironment);
        assertFalse(((ConfigurableEnvironment) environment).getPropertySources()
                .contains("justTestProjectFeignOkHttp"));
        assertNull(environment.getProperty("feign.okhttp.enabled"));
        assertNull(environment.getProperty("spring.cloud.openfeign.okhttp.enabled"));
        assertFalse(applicationContext.containsBean("client"));
        context.setResult("opt-in");
    }
}

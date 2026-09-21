package com.just.test.demo.project.feignoffapp.defaults;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.annotation.JustTestProject;
import com.just.test.context.CaseContext;
import com.just.test.demo.project.feignoffapp.JustTestApplication;
import com.just.test.demo.project.fixtures.feign.DemoFeignClient;
import com.just.test.demo.project.fixtures.feign.DemoLocalClient;
import com.just.test.lifecycle.JustTestLifecycle;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JustTest
class DefaultLeavesFeignAndOkHttpJustTest implements JustTestLifecycle {

    @Autowired
    private DemoLocalClient demoLocalClient;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @CaseSource
    void doesNotForceOkHttpOffOrAutoMockFeign(CaseContext context) {
        JustTestProject project = JustTestApplication.class.getAnnotation(JustTestProject.class);
        assertFalse(project.autoMockFeignClients());
        assertEquals(0, project.autoMockFeignClientExcludes().length);
        assertFalse(project.enableFeignOkHttp());

        assertTrue(environment instanceof ConfigurableEnvironment);
        assertFalse(((ConfigurableEnvironment) environment).getPropertySources()
                .contains("justTestProjectFeignOkHttp"));
        assertNull(environment.getProperty("feign.okhttp.enabled"));
        assertNull(environment.getProperty("spring.cloud.openfeign.okhttp.enabled"));

        assertEquals(0, applicationContext.getBeanNamesForType(DemoFeignClient.class).length);
        assertFalse(Mockito.mockingDetails(demoLocalClient).isMock());
        assertEquals("real-local-client", demoLocalClient.id());
        context.setResult("defaults");
    }
}

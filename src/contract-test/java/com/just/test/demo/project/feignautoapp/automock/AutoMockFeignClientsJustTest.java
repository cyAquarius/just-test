package com.just.test.demo.project.feignautoapp.automock;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.annotation.JustTestProject;
import com.just.test.context.CaseContext;
import com.just.test.demo.project.feignautoapp.JustTestApplication;
import com.just.test.demo.project.fixtures.feign.DemoFeignClient;
import com.just.test.demo.project.fixtures.feign.DemoFeignGateway;
import com.just.test.demo.project.fixtures.feign.DemoLocalClient;
import com.just.test.demo.project.fixtures.feign.ExcludedFeignClient;
import com.just.test.lifecycle.JustTestLifecycle;
import org.mockito.Mockito;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@JustTest
class AutoMockFeignClientsJustTest implements JustTestLifecycle {

    @Autowired
    private DemoFeignClient demoFeignClient;

    @Autowired
    private DemoFeignGateway demoFeignGateway;

    @Autowired
    private DemoLocalClient demoLocalClient;

    @Autowired
    private ApplicationContext applicationContext;

    @CaseSource
    void mocksAnnotatedFeignClientsOnly(CaseContext context) throws Exception {
        JustTestProject project = JustTestApplication.class.getAnnotation(JustTestProject.class);
        assertTrue(project.autoMockFeignClients());
        assertEquals(1, project.autoMockFeignClientExcludes().length);
        assertEquals(ExcludedFeignClient.class, project.autoMockFeignClientExcludes()[0]);

        assertTrue(Mockito.mockingDetails(currentTarget(demoFeignClient)).isMock());
        when(demoFeignClient.ping()).thenReturn("auto-mocked");
        assertEquals("auto-mocked", demoFeignGateway.ping());

        assertFalse(Mockito.mockingDetails(demoLocalClient).isMock());
        assertEquals("real-local-client", demoLocalClient.id());
        assertEquals(0, applicationContext.getBeanNamesForType(ExcludedFeignClient.class).length);
        context.setResult("auto-mocked");
    }

    private static Object currentTarget(Object bean) throws Exception {
        if (bean instanceof Advised) {
            return ((Advised) bean).getTargetSource().getTarget();
        }
        return bean;
    }
}

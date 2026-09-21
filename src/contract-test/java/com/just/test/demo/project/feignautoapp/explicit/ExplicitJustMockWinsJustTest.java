package com.just.test.demo.project.feignautoapp.explicit;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.demo.project.fixtures.feign.DemoFeignClient;
import com.just.test.demo.project.fixtures.feign.DemoFeignGateway;
import com.just.test.lifecycle.JustTestLifecycle;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

@JustTest
class ExplicitJustMockWinsJustTest implements JustTestLifecycle {

    @JustMock
    private DemoFeignClient demoFeignClient;

    @Autowired
    private DemoFeignGateway demoFeignGateway;

    @Autowired
    private ApplicationContext applicationContext;

    @CaseSource
    void explicitJustMockWinsOverAutoFeignMock(CaseContext context) {
        assertEquals(1, applicationContext.getBeanNamesForType(DemoFeignClient.class).length);
        when(demoFeignClient.ping()).thenReturn("from-just-mock");
        assertEquals("from-just-mock", demoFeignGateway.ping());
        assertSame(demoFeignClient, currentTarget(applicationContext.getBean(DemoFeignClient.class)));
        context.setResult("from-just-mock");
    }

    private static Object currentTarget(Object bean) {
        try {
            if (bean instanceof Advised) {
                return ((Advised) bean).getTargetSource().getTarget();
            }
            return bean;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}

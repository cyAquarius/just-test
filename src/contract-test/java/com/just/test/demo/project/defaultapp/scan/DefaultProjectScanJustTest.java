package com.just.test.demo.project.defaultapp.scan;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.annotation.JustTestProject;
import com.just.test.context.CaseContext;
import com.just.test.demo.project.defaultapp.JustTestApplication;
import com.just.test.demo.project.fixtures.scan.DemoMarkerService;
import com.just.test.demo.project.fixtures.scan.DemoProductionApplication;
import com.just.test.demo.project.fixtures.scan.DemoSchedulingConfig;
import com.just.test.demo.project.fixtures.scan.DemoWebController;
import com.just.test.demo.project.fixtures.scan.ExtraService;
import com.just.test.demo.project.fixtures.scan.PlainCollaborator;
import com.just.test.lifecycle.JustTestLifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JustTest
class DefaultProjectScanJustTest implements JustTestLifecycle {

    @Autowired
    private DemoMarkerService markerService;

    @Autowired
    private ExtraService extraService;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @CaseSource
    void appliesDefaultScanAndDataSourceAliases(CaseContext context) {
        assertNotNull(markerService);
        assertNotNull(extraService);
        assertFalse(applicationContext.getBeanNamesForType(DemoWebController.class).length > 0);
        assertFalse(applicationContext.getBeanNamesForType(DemoProductionApplication.class).length > 0);
        assertFalse(applicationContext.getBeanNamesForType(DemoSchedulingConfig.class).length > 0);
        assertFalse(applicationContext.getBeanNamesForType(PlainCollaborator.class).length > 0);
        assertTrue(applicationContext.containsBean("dataSource"));
        assertTrue(applicationContext.containsBean("transactionManager"));
        assertSame(applicationContext.getBean("justTestDataSource"),
                applicationContext.getBean("dataSource"));
        assertSame(applicationContext.getBean("justTestTransactionManager"),
                applicationContext.getBean("transactionManager"));
        assertSame(applicationContext.getBean("justTestDataSource", DataSource.class),
                applicationContext.getBean(DataSource.class));
        assertFalse(applicationContext.containsBean("sqlSessionFactory"));
        assertFalse(JustTestApplication.class.getAnnotation(JustTestProject.class).enableFeignOkHttp());
        assertEquals(0, JustTestApplication.class.getAnnotation(JustTestProject.class)
                .excludeAutoConfiguration().length);
        assertTrue(environment instanceof ConfigurableEnvironment);
        assertTrue(((ConfigurableEnvironment) environment).getPropertySources()
                .contains("justTestProjectFeignOkHttp"));
        assertEquals(Boolean.FALSE, environment.getProperty("feign.okhttp.enabled", Boolean.class));
        assertEquals(Boolean.FALSE,
                environment.getProperty("spring.cloud.openfeign.okhttp.enabled", Boolean.class));
        assertFalse(applicationContext.containsBean("client"));
        context.setResult(markerService.marker());
    }
}

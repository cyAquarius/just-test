package com.just.test.smarttest.demo.project.defaultapp.scan;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.annotation.SmartTestProject;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.demo.project.defaultapp.SmartTestApplication;
import com.just.test.smarttest.demo.project.fixtures.scan.DemoMarkerService;
import com.just.test.smarttest.demo.project.fixtures.scan.DemoProductionApplication;
import com.just.test.smarttest.demo.project.fixtures.scan.DemoSchedulingConfig;
import com.just.test.smarttest.demo.project.fixtures.scan.DemoWebController;
import com.just.test.smarttest.demo.project.fixtures.scan.ExtraService;
import com.just.test.smarttest.demo.project.fixtures.scan.PlainCollaborator;
import com.just.test.smarttest.lifecycle.SmartTestLifecycle;
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

@SmartTest
class DefaultProjectScanSmartTest implements SmartTestLifecycle {

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
        assertSame(applicationContext.getBean("smartTestDataSource"),
                applicationContext.getBean("dataSource"));
        assertSame(applicationContext.getBean("smartTestTransactionManager"),
                applicationContext.getBean("transactionManager"));
        assertSame(applicationContext.getBean("smartTestDataSource", DataSource.class),
                applicationContext.getBean(DataSource.class));
        assertFalse(applicationContext.containsBean("sqlSessionFactory"));
        assertFalse(SmartTestApplication.class.getAnnotation(SmartTestProject.class).enableFeignOkHttp());
        assertEquals(0, SmartTestApplication.class.getAnnotation(SmartTestProject.class)
                .excludeAutoConfiguration().length);
        assertTrue(environment instanceof ConfigurableEnvironment);
        assertTrue(((ConfigurableEnvironment) environment).getPropertySources()
                .contains("smartTestProjectFeignOkHttp"));
        assertEquals(Boolean.FALSE, environment.getProperty("feign.okhttp.enabled", Boolean.class));
        assertEquals(Boolean.FALSE,
                environment.getProperty("spring.cloud.openfeign.okhttp.enabled", Boolean.class));
        assertFalse(applicationContext.containsBean("client"));
        context.setResult(markerService.marker());
    }
}

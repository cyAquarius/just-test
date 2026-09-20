package com.just.test.smarttest.demo.project.aliasapp.compat;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.lifecycle.SmartTestLifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SmartTest
class ProjectAliasSmartTest implements SmartTestLifecycle {

    @Autowired
    @Qualifier("masterDataSource")
    private DataSource masterDataSource;

    @Autowired
    @Qualifier("masterDataTransactionManager")
    private DataSourceTransactionManager masterTransactionManager;

    @Autowired
    private ApplicationContext applicationContext;

    @CaseSource
    void registersLegacyAliasesAndSkipsConflicts(CaseContext context) {
        Object smartTestDataSource = applicationContext.getBean("smartTestDataSource");
        Object smartTestTransactionManager = applicationContext.getBean("smartTestTransactionManager");

        assertTrue(applicationContext.containsBean("dataSource"));
        assertTrue(applicationContext.containsBean("transactionManager"));
        assertSame(smartTestDataSource, applicationContext.getBean("dataSource"));
        assertSame(smartTestDataSource, masterDataSource);
        assertSame(smartTestTransactionManager, applicationContext.getBean("transactionManager"));
        assertSame(smartTestTransactionManager, masterTransactionManager);
        assertNotSame(smartTestDataSource, applicationContext.getBean("occupiedDataSource"));
        context.setResult(context.getString("status"));
    }
}

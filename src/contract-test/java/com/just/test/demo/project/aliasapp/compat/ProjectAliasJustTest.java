package com.just.test.demo.project.aliasapp.compat;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JustTest
class ProjectAliasJustTest implements JustTestLifecycle {

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
        Object justTestDataSource = applicationContext.getBean("justTestDataSource");
        Object justTestTransactionManager = applicationContext.getBean("justTestTransactionManager");

        assertTrue(applicationContext.containsBean("dataSource"));
        assertTrue(applicationContext.containsBean("transactionManager"));
        assertSame(justTestDataSource, applicationContext.getBean("dataSource"));
        assertSame(justTestDataSource, masterDataSource);
        assertSame(justTestTransactionManager, applicationContext.getBean("transactionManager"));
        assertSame(justTestTransactionManager, masterTransactionManager);
        assertNotSame(justTestDataSource, applicationContext.getBean("occupiedDataSource"));
        context.setResult(context.getString("status"));
    }
}

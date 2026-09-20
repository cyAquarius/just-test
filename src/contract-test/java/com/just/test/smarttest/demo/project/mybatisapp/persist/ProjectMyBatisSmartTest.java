package com.just.test.smarttest.demo.project.mybatisapp.persist;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.demo.project.fixtures.persist.ProjectRecordService;
import com.just.test.smarttest.lifecycle.SmartTestLifecycle;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertSame;

@SmartTest
class ProjectMyBatisSmartTest implements SmartTestLifecycle {

    @Autowired
    private ProjectRecordService recordService;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Autowired
    private ApplicationContext applicationContext;

    @CaseSource
    void writesThroughSmartTestDataSource(CaseContext context) {
        DataSource dataSource = applicationContext.getBean("smartTestDataSource", DataSource.class);
        assertSame(dataSource, sqlSessionFactory.getConfiguration().getEnvironment().getDataSource());
        assertSame(dataSource, applicationContext.getBean("dataSource"));
        context.setResult(recordService.insertAndFind(context.getString("marker")));
    }
}

package com.just.test.demo.project.mybatisapp.persist;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.demo.project.fixtures.persist.ProjectRecordService;
import com.just.test.lifecycle.JustTestLifecycle;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertSame;

@JustTest
class ProjectMyBatisJustTest implements JustTestLifecycle {

    @Autowired
    private ProjectRecordService recordService;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Autowired
    private ApplicationContext applicationContext;

    @CaseSource
    void writesThroughJustTestDataSource(CaseContext context) {
        DataSource dataSource = applicationContext.getBean("justTestDataSource", DataSource.class);
        assertSame(dataSource, sqlSessionFactory.getConfiguration().getEnvironment().getDataSource());
        assertSame(dataSource, applicationContext.getBean("dataSource"));
        context.setResult(recordService.insertAndFind(context.getString("marker")));
    }
}

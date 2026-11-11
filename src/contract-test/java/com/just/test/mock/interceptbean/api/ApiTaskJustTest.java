package com.just.test.mock.interceptbean.api;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import com.just.test.mock.interceptbean.SameBeanContextProbe;
import com.just.test.mock.interceptbean.fixtures.TaskReader;
import com.just.test.mock.interceptbean.fixtures.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/** 按接口声明 {@code @JustMock}。 */
@JustTest
public class ApiTaskJustTest implements JustTestLifecycle {

    @JustMock
    private TaskService taskService;

    @Autowired
    private TaskReader taskReader;

    @Autowired
    private ApplicationContext applicationContext;

    @CaseSource
    void stubsTheInterface(CaseContext context) throws Exception {
        SameBeanContextProbe.share(applicationContext);
        SameBeanContextProbe.assertRegisteredOnce(applicationContext, "taskServiceImpl");
        SameBeanContextProbe.assertBound(taskService, applicationContext, "taskServiceImpl");
        assertNull(taskReader.code());
        when(taskService.code()).thenReturn("stub-" + context.getCaseName());
        assertEquals("stub-" + context.getCaseName(), taskReader.code());
        assertNull(taskReader.label());
    }
}

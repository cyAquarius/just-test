package com.just.test.mock.interceptbean.impl;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import com.just.test.mock.interceptbean.SameBeanContextProbe;
import com.just.test.mock.interceptbean.fixtures.TaskReader;
import com.just.test.mock.interceptbean.fixtures.TaskServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/** 按实现类声明 {@code @JustMock}。 */
@JustTest
public class ImplTaskJustTest implements JustTestLifecycle {

    @JustMock
    private TaskServiceImpl taskServiceImpl;

    @Autowired
    private TaskReader taskReader;

    @Autowired
    private ApplicationContext applicationContext;

    @CaseSource
    void stubsTheImplementation(CaseContext context) throws Exception {
        SameBeanContextProbe.share(applicationContext);
        SameBeanContextProbe.assertRegisteredOnce(applicationContext, "taskServiceImpl");
        SameBeanContextProbe.assertBound(taskServiceImpl, applicationContext, "taskServiceImpl");
        assertNull(taskReader.code());
        when(taskServiceImpl.code()).thenReturn("impl-" + context.getCaseName());
        assertEquals("impl-" + context.getCaseName(), taskReader.code());
    }
}

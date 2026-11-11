package com.just.test.mock.interceptbean.plain;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import com.just.test.mock.interceptbean.SameBeanContextProbe;
import com.just.test.mock.interceptbean.fixtures.TaskReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 没有声明该 Bean 的 {@code @JustMock}，未 stub 调用必须走真实 Bean。 */
@JustTest
public class PlainTaskJustTest implements JustTestLifecycle {

    @Autowired
    private TaskReader taskReader;

    @Autowired
    private ApplicationContext applicationContext;

    @CaseSource
    void realBeanDoesNotRecurse(CaseContext context) {
        SameBeanContextProbe.share(applicationContext);
        SameBeanContextProbe.assertRegisteredOnce(applicationContext, "taskServiceImpl");
        assertEquals("live-code", taskReader.code());
        assertEquals("live-code", taskReader.label());
    }
}

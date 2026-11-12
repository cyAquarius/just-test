package com.just.test.demo.mapperintercept.autounion.mocked;

import com.just.test.demo.mapperintercept.autounion.SampleMapper;
import com.just.test.demo.mapperintercept.autounion.ContextProbe;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.annotation.JustMock;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@JustTest
class MockedMapperJustTest implements JustTestLifecycle {
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private SampleMapper consumer;
    @JustMock
    private SampleMapper mapper;

    @CaseSource
    void checksMapper(CaseContext context) {
        ContextProbe.check(applicationContext);
        assertNull(consumer.value());
        when(mapper.value()).thenReturn(context.getCaseName());
        assertEquals(context.getCaseName(), consumer.value());
    }
}

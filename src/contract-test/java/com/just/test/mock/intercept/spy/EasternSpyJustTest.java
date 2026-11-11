package com.just.test.mock.intercept.spy;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustSpy;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import com.just.test.mock.intercept.InterceptContextProbe;
import com.just.test.mock.intercept.fixtures.ClockReader;
import com.just.test.mock.intercept.fixtures.EasternClock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;

@JustTest
public class EasternSpyJustTest implements JustTestLifecycle {

    @JustSpy
    private EasternClock easternClock;

    @Autowired
    private ClockReader clockReader;

    @Autowired
    private ApplicationContext applicationContext;

    @CaseSource
    void spyWrapsTheRealBeanInsteadOfTheIntercept(CaseContext context) {
        InterceptContextProbe.distinctSpy(applicationContext);
        assertEquals("eastern-live", clockReader.easternNow());
        assertEquals("western-live", clockReader.westernNow());
        doReturn("spy-stub").when(easternClock).now();
        assertEquals("spy-stub", clockReader.easternNow());
        assertEquals("eastern-live", clockReader.easternZone());
        ConfigurableListableBeanFactory factory =
                ((ConfigurableApplicationContext) applicationContext).getBeanFactory();
        assertTrue(factory.containsBeanDefinition("justSpy.target.easternClock"));
        assertFalse(factory.containsBeanDefinition("justMock.intercept.target.easternClock"));
        BeanDefinition hidden = factory.getBeanDefinition("justSpy.target.easternClock");
        String className = hidden.getBeanClassName();
        assertTrue(className == null || !className.contains("ScopedProxy"), className);
    }
}

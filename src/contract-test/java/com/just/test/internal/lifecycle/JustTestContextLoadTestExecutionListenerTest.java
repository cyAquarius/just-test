package com.just.test.internal.lifecycle;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.test.context.TestExecutionListener;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JustTestContextLoadTestExecutionListenerTest {

    @Test
    void isRegisteredAsSpringTestExecutionListener() {
        List<String> listeners = SpringFactoriesLoader.loadFactoryNames(
                TestExecutionListener.class, getClass().getClassLoader());
        assertTrue(listeners.contains(JustTestContextLoadTestExecutionListener.class.getName()),
                listeners.toString());
    }
}

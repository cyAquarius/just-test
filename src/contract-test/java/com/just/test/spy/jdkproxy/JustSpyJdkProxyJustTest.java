package com.just.test.spy.jdkproxy;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustSpy;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;

/**
 * Feign 客户端是接口的 JDK 代理。这里用 {@link Proxy} 覆盖同一委托路径，
 * 不引入 OpenFeign。MyBatis {@code MapperFactoryBean} 的 JDK 代理另见 mapper 契约测试。
 */
@JustTest
@ContextConfiguration(classes = JustSpyJdkProxyJustTest.TestConfiguration.class)
class JustSpyJdkProxyJustTest implements JustTestLifecycle {

    @Autowired
    private EchoConsumer echoConsumer;

    @JustSpy
    private EchoClient echoClient;

    @CaseSource
    void unstubbedJdkProxyMethodIsNotAMockitoDefault(CaseContext context) {
        doReturn("stub-name").when(echoClient).name();

        assertEquals("real-ping", echoConsumer.ping());
        assertEquals("stub-name", echoConsumer.name());
    }

    public interface EchoClient {
        String ping();

        String name();
    }

    static class EchoConsumer {
        private final EchoClient echoClient;

        EchoConsumer(EchoClient echoClient) {
            this.echoClient = echoClient;
        }

        String ping() {
            return echoClient.ping();
        }

        String name() {
            return echoClient.name();
        }
    }

    static final class EchoHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                if ("hashCode".equals(method.getName())) {
                    return System.identityHashCode(proxy);
                }
                if ("equals".equals(method.getName())) {
                    return proxy == args[0];
                }
                return "EchoProxy";
            }
            return "real-" + method.getName();
        }
    }

    @Configuration
    static class TestConfiguration {
        @Bean
        EchoClient echoClient() {
            return (EchoClient) Proxy.newProxyInstance(
                    EchoClient.class.getClassLoader(),
                    new Class<?>[] {EchoClient.class},
                    new EchoHandler());
        }

        @Bean
        EchoConsumer echoConsumer(EchoClient echoClient) {
            return new EchoConsumer(echoClient);
        }
    }
}

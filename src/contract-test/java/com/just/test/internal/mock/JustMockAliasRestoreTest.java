package com.just.test.internal.mock;

import com.just.test.annotation.JustMock;
import com.just.test.context.CaseContext;
import com.just.test.internal.context.CaseExecutionContext;
import com.just.test.internal.scope.ThreadScope;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JustMockAliasRestoreTest {

    @Test
    void qualifierAliasInjectsThreadScopedMockAfterReplacement() throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(AliasedClockConfiguration.class);
        context.getBeanFactory().registerScope("thread", new ThreadScope());
        context.addBeanFactoryPostProcessor(new JustMockPostProcessor(
                Collections.singleton(definition(field(ClockField.class, "clock")))));
        try {
            context.refresh();
            CaseExecutionContext.bind(new CaseContext("alias-replace", "alias-replace"));
            DefaultListableBeanFactory beanFactory =
                    (DefaultListableBeanFactory) context.getBeanFactory();
            assertEquals("clock", beanFactory.canonicalName("liveClock"));
            assertTrue(beanFactory.isAlias("liveClock"));
            assertFalse(beanFactory.containsBeanDefinition("liveClock"));

            ClockReader reader = context.getBean(ClockReader.class);
            assertNull(reader.now());
            Clock mock = (Clock) threadTarget(context.getBean("liveClock"));
            assertTrue(Mockito.mockingDetails(mock).isMock());
            assertSame(mock, threadTarget(context.getBean("clock")));
            assertSame(mock, threadTarget(reader.clock));
            Mockito.when(mock.now()).thenReturn("frozen");
            assertEquals("frozen", reader.now());
        } finally {
            CaseExecutionContext.clear();
            ThreadScope.clearCurrentThread();
            context.close();
        }
    }

    @Test
    void qualifierAliasInjectsThreadScopedMockAfterInterceptHide() throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(AliasedClockConfiguration.class);
        context.getBeanFactory().registerScope("thread", new ThreadScope());
        context.addBeanFactoryPostProcessor(new JustMockPostProcessor(
                Collections.<JustMockDefinition>emptySet(),
                Collections.<JustMockDefinition>emptySet(),
                JustMockInterceptPlan.project(
                        JustMockAliasRestoreTest.class,
                        Collections.<Class<?>>singleton(Clock.class),
                        Collections.<Class<?>>emptySet())));
        try {
            context.refresh();
            CaseExecutionContext.bind(new CaseContext("alias-intercept", "alias-intercept"));
            DefaultListableBeanFactory beanFactory =
                    (DefaultListableBeanFactory) context.getBeanFactory();
            assertEquals("clock", beanFactory.canonicalName("liveClock"));
            assertTrue(beanFactory.isAlias("liveClock"));
            assertTrue(beanFactory.containsBeanDefinition(
                    JustMockPostProcessor.INTERCEPT_TARGET_PREFIX + "clock"));
            assertFalse(beanFactory.getBeanDefinition(
                    JustMockPostProcessor.INTERCEPT_TARGET_PREFIX + "clock").isAutowireCandidate());

            ClockReader reader = context.getBean(ClockReader.class);
            assertEquals("live", reader.now());
            Clock mock = (Clock) threadTarget(context.getBean("liveClock"));
            assertTrue(Mockito.mockingDetails(mock).isMock());
            assertSame(mock, threadTarget(context.getBean("clock")));
            assertSame(mock, threadTarget(reader.clock));
            Mockito.when(mock.now()).thenReturn("frozen");
            assertEquals("frozen", reader.now());
        } finally {
            CaseExecutionContext.clear();
            ThreadScope.clearCurrentThread();
            context.close();
        }
    }

    @Test
    void skipsAliasThatConflictsWithAnExistingBeanAndContinues() throws Exception {
        OccupyingFactory factory = new OccupyingFactory();
        GenericApplicationContext context = new GenericApplicationContext(factory);
        new AnnotatedBeanDefinitionReader(context).register(AliasedClockConfiguration.class);
        factory.registerScope("thread", new ThreadScope());
        context.addBeanFactoryPostProcessor(new ExtraAliasPostProcessor());
        context.addBeanFactoryPostProcessor(new JustMockPostProcessor(
                Collections.singleton(definition(field(ClockField.class, "clock")))));

        PrintStream previous = System.err;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setErr(new PrintStream(buffer, true, "UTF-8"));
        try {
            context.refresh();
            CaseExecutionContext.bind(new CaseContext("alias-conflict", "alias-conflict"));
            String logs = buffer.toString("UTF-8");
            assertTrue(logs.contains(
                    "Skipping alias 'occupiedClockAlias' for bean 'clock'"));
            assertTrue(logs.contains("left unchanged"));

            assertEquals("clock", factory.canonicalName("liveClock"));
            assertTrue(factory.isAlias("liveClock"));
            assertTrue(Arrays.asList(factory.getAliases("clock")).contains("liveClock"));
            assertEquals(Occupied.class.getName(),
                    factory.getBeanDefinition("occupiedClockAlias").getBeanClassName());
            assertFalse(Arrays.asList(factory.getAliases("clock")).contains("occupiedClockAlias"),
                    "a conflicting alias must not be attached back onto the replaced bean");

            ClockReader reader = context.getBean(ClockReader.class);
            Clock mock = (Clock) threadTarget(context.getBean("liveClock"));
            assertTrue(Mockito.mockingDetails(mock).isMock());
            assertSame(mock, threadTarget(reader.clock));
            Mockito.when(mock.now()).thenReturn("frozen");
            assertEquals("frozen", reader.now());
            assertEquals(Occupied.class, context.getBean("occupiedClockAlias").getClass());
        } finally {
            System.setErr(previous);
            CaseExecutionContext.clear();
            ThreadScope.clearCurrentThread();
            context.close();
        }
    }

    private static Object threadTarget(Object bean) throws Exception {
        if (bean instanceof Advised) {
            return ((Advised) bean).getTargetSource().getTarget();
        }
        return bean;
    }

    private static JustMockDefinition definition(Field mockField) {
        return JustMockDefinition.forField(mockField, mockField.getAnnotation(JustMock.class));
    }

    private static Field field(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException ex) {
            throw new AssertionError(ex);
        }
    }

    /**
     * 在 {@code removeBeanDefinition("clock")} 之后把其中一个别名占用成独立 Bean，
     * 模拟恢复别名时名字已经被别人注册。
     */
    static final class OccupyingFactory extends DefaultListableBeanFactory {
        @Override
        public void removeBeanDefinition(String beanName) throws NoSuchBeanDefinitionException {
            super.removeBeanDefinition(beanName);
            if ("clock".equals(beanName) && !containsBeanDefinition("occupiedClockAlias")) {
                registerBeanDefinition("occupiedClockAlias",
                        BeanDefinitionBuilder.genericBeanDefinition(Occupied.class).getBeanDefinition());
            }
        }
    }

    static final class ExtraAliasPostProcessor implements BeanFactoryPostProcessor {
        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
            ((BeanDefinitionRegistry) beanFactory).registerAlias("clock", "occupiedClockAlias");
        }
    }

    static class ClockField {
        @JustMock
        private Clock clock;
    }

    public static class Clock {
        public String now() {
            return "live";
        }
    }

    public static class Occupied {
    }

    public static class ClockReader {
        private final Clock clock;

        public ClockReader(Clock clock) {
            this.clock = clock;
        }

        public String now() {
            return clock.now();
        }
    }

    @Configuration
    public static class AliasedClockConfiguration {
        @Bean(name = {"clock", "liveClock"})
        public Clock clock() {
            return new Clock();
        }

        @Bean
        public ClockReader clockReader(@Qualifier("liveClock") Clock clock) {
            return new ClockReader(clock);
        }
    }
}

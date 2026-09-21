package com.just.test.internal.project;

import com.just.test.demo.project.fixtures.feign.DemoFeignClient;
import com.just.test.demo.project.fixtures.feign.DemoLocalClient;
import com.just.test.demo.project.fixtures.feign.ExcludedFeignClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.util.ClassUtils;

import java.lang.annotation.Annotation;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JustTestProjectFeignAutoMockTest {

    @Test
    void discoversAnnotatedInterfacesAndSkipsNameOnlyClients() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        JustTestProjectFeignAutoMock settings = new JustTestProjectFeignAutoMock(
                new String[] {"com.just.test.demo.project.fixtures.feign"},
                new Class<?>[] {ExcludedFeignClient.class});

        Map<Class<?>, String> discovered = settings.discover(beanFactory);

        assertTrue(discovered.containsKey(DemoFeignClient.class));
        assertEquals("", discovered.get(DemoFeignClient.class));
        assertFalse(discovered.containsKey(ExcludedFeignClient.class));
        assertFalse(discovered.containsKey(DemoLocalClient.class));
    }

    @Test
    void prefersExistingFeignFactoryBeanName() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        GenericBeanDefinition factoryBean = new GenericBeanDefinition();
        factoryBean.setBeanClassName(JustTestProjectFeignAutoMock.FEIGN_CLIENT_FACTORY_BEAN);
        factoryBean.getPropertyValues().add("type", DemoFeignClient.class);
        beanFactory.registerBeanDefinition("demo-feign", factoryBean);
        beanFactory.registerBeanDefinition("unrelated",
                BeanDefinitionBuilder.genericBeanDefinition(DemoLocalClient.class).getBeanDefinition());

        JustTestProjectFeignAutoMock settings = new JustTestProjectFeignAutoMock(
                new String[] {"com.just.test.demo.project.fixtures.feign"}, new Class<?>[0]);

        Map<Class<?>, String> discovered = settings.discover(beanFactory);

        assertEquals("demo-feign", discovered.get(DemoFeignClient.class));
        assertFalse(discovered.containsKey(DemoLocalClient.class));
    }

    @Test
    void isNoOpWhenFeignAnnotationIsHidden() {
        ClassLoader hiding = new ClassLoader(ClassUtils.getDefaultClassLoader()) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (JustTestProjectFeignAutoMock.FEIGN_CLIENT_ANNOTATION.equals(name)) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name);
            }
        };
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.setBeanClassLoader(hiding);
        JustTestProjectFeignAutoMock settings = new JustTestProjectFeignAutoMock(
                new String[] {"com.just.test.demo.project.fixtures.feign"}, new Class<?>[0]);

        assertTrue(settings.discover(beanFactory).isEmpty());
        assertTrue(JustTestProjectFeignAutoMock.loadAnnotation(
                JustTestProjectFeignAutoMock.FEIGN_CLIENT_ANNOTATION, hiding) == null);
    }

    @Test
    void recognizesOnlyFeignClientInterfaces() {
        Class<? extends Annotation> feignClient = JustTestProjectFeignAutoMock.loadAnnotation(
                JustTestProjectFeignAutoMock.FEIGN_CLIENT_ANNOTATION, ClassUtils.getDefaultClassLoader());
        assertTrue(JustTestProjectFeignAutoMock.isFeignClient(DemoFeignClient.class, feignClient));
        assertFalse(JustTestProjectFeignAutoMock.isFeignClient(DemoLocalClient.class, feignClient));
        assertFalse(JustTestProjectFeignAutoMock.isFeignClient(String.class, feignClient));
    }
}

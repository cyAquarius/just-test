package com.just.test.internal.project;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.util.ClassUtils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JustTestProjectRegistrarTest {

    @Test
    void requiresExplicitBasePackages() {
        AnnotationAttributes attributes = new AnnotationAttributes();
        attributes.put("basePackages", new String[0]);
        attributes.put("value", new String[] {"  "});

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> JustTestProjectRegistrar.requiredBasePackages(attributes));

        assertTrue(failure.getMessage().contains("requires basePackages"));
        assertTrue(failure.getMessage().contains("does not guess"));
    }

    @Test
    void trimsAndPrefersBasePackages() {
        AnnotationAttributes attributes = new AnnotationAttributes();
        attributes.put("basePackages", new String[] {" com.example "});
        attributes.put("value", new String[] {"com.ignored"});

        assertArrayEquals(new String[] {"com.example"},
                JustTestProjectRegistrar.requiredBasePackages(attributes));
    }

    @Test
    void failsFastWhenMapperPackagesSetWithoutMyBatisSpring() {
        ClassLoader hiding = new ClassLoader(ClassUtils.getDefaultClassLoader()) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (name != null && name.startsWith("org.mybatis.spring")) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name);
            }
        };

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> JustTestProjectRegistrar.requireMyBatisSpringIfNeeded(
                        new String[] {"com.example.mapper"}, hiding));

        assertTrue(failure.getMessage().contains("mapperPackages requires mybatis-spring"));
        assertTrue(failure.getMessage().contains(JustTestProjectRegistrar.MYBATIS_SPRING_FACTORY));
    }

    @Test
    void ignoresEmptyMapperPackagesWhenMyBatisIsAbsent() {
        ClassLoader hiding = new ClassLoader(ClassUtils.getDefaultClassLoader()) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (name != null && name.startsWith("org.mybatis.spring")) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name);
            }
        };

        JustTestProjectRegistrar.requireMyBatisSpringIfNeeded(new String[0], hiding);
    }

    @Test
    void doesNotRegisterFeignAutoMockWhenFlagIsAbsentOrFalse() {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        JustTestProjectRegistrar.registerFeignAutoMockIfRequested(
                registry, new AnnotationAttributes(), new String[] {"com.example"});
        assertFalse(registry.containsBeanDefinition(JustTestProjectFeignAutoMock.BEAN_NAME));

        AnnotationAttributes disabled = new AnnotationAttributes();
        disabled.put(JustTestProjectFeignAutoMock.ATTRIBUTE, false);
        JustTestProjectRegistrar.registerFeignAutoMockIfRequested(
                registry, disabled, new String[] {"com.example"});
        assertFalse(registry.containsBeanDefinition(JustTestProjectFeignAutoMock.BEAN_NAME));
    }

    @Test
    void registersFeignAutoMockSettingsWhenOptedIn() {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        AnnotationAttributes attributes = new AnnotationAttributes();
        attributes.put(JustTestProjectFeignAutoMock.ATTRIBUTE, true);
        attributes.put(JustTestProjectFeignAutoMock.EXCLUDES_ATTRIBUTE, new Class<?>[] {Runnable.class});

        JustTestProjectRegistrar.registerFeignAutoMockIfRequested(
                registry, attributes, new String[] {"com.example.feign"});

        assertTrue(registry.containsBeanDefinition(JustTestProjectFeignAutoMock.BEAN_NAME));
        JustTestProjectFeignAutoMock settings = registry.getBean(
                JustTestProjectFeignAutoMock.BEAN_NAME, JustTestProjectFeignAutoMock.class);
        assertArrayEquals(new String[] {"com.example.feign"}, settings.getBasePackages());
        assertTrue(settings.getExcludeClassNames().contains(Runnable.class.getName()));
    }
}

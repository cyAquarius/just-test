package com.just.test.smarttest.internal.project;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.util.ClassUtils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartTestProjectRegistrarTest {

    @Test
    void requiresExplicitBasePackages() {
        AnnotationAttributes attributes = new AnnotationAttributes();
        attributes.put("basePackages", new String[0]);
        attributes.put("value", new String[] {"  "});

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> SmartTestProjectRegistrar.requiredBasePackages(attributes));

        assertTrue(failure.getMessage().contains("requires basePackages"));
        assertTrue(failure.getMessage().contains("does not guess"));
    }

    @Test
    void trimsAndPrefersBasePackages() {
        AnnotationAttributes attributes = new AnnotationAttributes();
        attributes.put("basePackages", new String[] {" com.example "});
        attributes.put("value", new String[] {"com.ignored"});

        assertArrayEquals(new String[] {"com.example"},
                SmartTestProjectRegistrar.requiredBasePackages(attributes));
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
                () -> SmartTestProjectRegistrar.requireMyBatisSpringIfNeeded(
                        new String[] {"com.example.mapper"}, hiding));

        assertTrue(failure.getMessage().contains("mapperPackages requires mybatis-spring"));
        assertTrue(failure.getMessage().contains(SmartTestProjectRegistrar.MYBATIS_SPRING_FACTORY));
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

        SmartTestProjectRegistrar.requireMyBatisSpringIfNeeded(new String[0], hiding);
    }
}

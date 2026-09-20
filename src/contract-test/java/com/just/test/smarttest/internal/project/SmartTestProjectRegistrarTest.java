package com.just.test.smarttest.internal.project;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.ClassUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void defaultDisablesFeignOkHttpAndOverridesExistingTrue() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "application", Collections.<String, Object>singletonMap(
                        SmartTestProjectFeignOkHttp.BOOT2_OKHTTP_ENABLED, "true")));
        AnnotationAttributes attributes = feignOkHttpAttributes(false);

        SmartTestProjectFeignOkHttp.apply(environment, attributes);

        assertEquals(Boolean.FALSE,
                environment.getProperty(SmartTestProjectFeignOkHttp.BOOT2_OKHTTP_ENABLED, Boolean.class));
        assertEquals(Boolean.FALSE,
                environment.getProperty(SmartTestProjectFeignOkHttp.BOOT3_OKHTTP_ENABLED, Boolean.class));
        assertTrue(environment.getPropertySources()
                .contains(SmartTestProjectFeignOkHttp.PROPERTY_SOURCE_NAME));
    }

    @Test
    void optInLeavesFeignOkHttpEnabledUntouched() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "application", Collections.<String, Object>singletonMap(
                        SmartTestProjectFeignOkHttp.BOOT2_OKHTTP_ENABLED, "true")));
        AnnotationAttributes attributes = feignOkHttpAttributes(true);

        SmartTestProjectFeignOkHttp.apply(environment, attributes);

        assertEquals(Boolean.TRUE,
                environment.getProperty(SmartTestProjectFeignOkHttp.BOOT2_OKHTTP_ENABLED, Boolean.class));
        assertNull(environment.getProperty(SmartTestProjectFeignOkHttp.BOOT3_OKHTTP_ENABLED));
        assertFalse(environment.getPropertySources()
                .contains(SmartTestProjectFeignOkHttp.PROPERTY_SOURCE_NAME));
    }

    @Test
    void disableIsIdempotent() {
        StandardEnvironment environment = new StandardEnvironment();
        SmartTestProjectFeignOkHttp.disableOkHttp(environment);
        SmartTestProjectFeignOkHttp.disableOkHttp(environment);
        assertEquals(1, countPropertySource(environment, SmartTestProjectFeignOkHttp.PROPERTY_SOURCE_NAME));
    }

    @Test
    void failsFastWhenEnvironmentIsNotConfigurable() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> SmartTestProjectFeignOkHttp.disableOkHttp(null));
        assertTrue(failure.getMessage().contains("cannot disable Feign OkHttp"));
        assertTrue(failure.getMessage().contains("client"));
    }

    private static AnnotationAttributes feignOkHttpAttributes(boolean enableFeignOkHttp) {
        AnnotationAttributes attributes = new AnnotationAttributes();
        attributes.put(SmartTestProjectFeignOkHttp.ATTRIBUTE, enableFeignOkHttp);
        return attributes;
    }

    private static int countPropertySource(StandardEnvironment environment, String name) {
        int count = 0;
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (name.equals(source.getName())) {
                count++;
            }
        }
        return count;
    }
}

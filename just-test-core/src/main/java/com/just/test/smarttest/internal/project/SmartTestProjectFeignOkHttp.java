package com.just.test.smarttest.internal.project;

import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 默认关闭 Feign OkHttp HTTP 客户端子配置，但不排除 {@code FeignAutoConfiguration}。
 *
 * <p>Boot 2 OpenFeign 使用 {@code feign.okhttp.enabled}；Boot 3 使用
 * {@code spring.cloud.openfeign.okhttp.enabled}。两者都写入测试 Environment，
 * 使 {@code OkHttpFeignConfiguration} 不注册名为 {@code client} 的
 * {@code OkHttpClient}，同时保留 {@code FeignContext} 给依赖包中的 Feign Client。</p>
 *
 * <p>不对消费方提供兼容承诺。</p>
 */
final class SmartTestProjectFeignOkHttp {

    static final String PROPERTY_SOURCE_NAME = "smartTestProjectFeignOkHttp";
    static final String ATTRIBUTE = "enableFeignOkHttp";
    /** Boot 2 / OpenFeign 3.x {@code @ConditionalOnProperty} on OkHttpFeignConfiguration. */
    static final String BOOT2_OKHTTP_ENABLED = "feign.okhttp.enabled";
    /** Boot 3 / OpenFeign 4.x renamed the same flag. */
    static final String BOOT3_OKHTTP_ENABLED = "spring.cloud.openfeign.okhttp.enabled";

    private SmartTestProjectFeignOkHttp() {
    }

    static void apply(Environment environment, AnnotationAttributes attributes) {
        if (isOptIn(attributes)) {
            return;
        }
        disableOkHttp(environment);
    }

    static boolean isOptIn(AnnotationAttributes attributes) {
        return attributes != null && attributes.containsKey(ATTRIBUTE) && attributes.getBoolean(ATTRIBUTE);
    }

    static void disableOkHttp(Environment environment) {
        if (!(environment instanceof ConfigurableEnvironment)) {
            throw new IllegalStateException(
                    "[SmartTest] @SmartTestProject cannot disable Feign OkHttp because the "
                            + "Environment is not configurable. OkHttpFeignConfiguration registers a "
                            + "bean named 'client' that collides with @Resource injection by field name.");
        }
        ConfigurableEnvironment configurable = (ConfigurableEnvironment) environment;
        if (configurable.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put(BOOT2_OKHTTP_ENABLED, "false");
        properties.put(BOOT3_OKHTTP_ENABLED, "false");
        configurable.getPropertySources().addFirst(
                new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
    }
}

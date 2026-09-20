package com.just.test.internal.mock;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 保存已解析的字段定义到逻辑 Bean 名的绑定，避免注入阶段再次按类型猜测。 */
public class JustMockBindings {

    private final Map<JustMockDefinition, String> beanNames;

    JustMockBindings(Map<JustMockDefinition, String> beanNames) {
        this.beanNames = Collections.unmodifiableMap(new LinkedHashMap<>(beanNames));
    }

    String getBeanName(JustMockDefinition definition) {
        return beanNames.get(definition);
    }
}

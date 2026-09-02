package com.just.test.smarttest.internal.mock;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 保存已解析的字段定义到逻辑 Bean 名的绑定，避免注入阶段再次按类型猜测。 */
public class SmartMockBindings {

    private final Map<SmartMockDefinition, String> beanNames;

    SmartMockBindings(Map<SmartMockDefinition, String> beanNames) {
        this.beanNames = Collections.unmodifiableMap(new LinkedHashMap<>(beanNames));
    }

    String getBeanName(SmartMockDefinition definition) {
        return beanNames.get(definition);
    }
}

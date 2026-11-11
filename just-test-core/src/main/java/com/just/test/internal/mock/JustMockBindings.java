package com.just.test.internal.mock;

import com.just.test.internal.project.JustTestProjectFeignAutoMock;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 保存已解析的字段定义到逻辑 Bean 名的绑定。
 *
 * <p>共享 Context 时，后启动的测试类不会再次跑 Bean 替换。字段若没有精确绑定，
 * 就按类型对上已经注册的 {@code @ThreadScopedMock} 或 Feign auto-mock。</p>
 */
public class JustMockBindings {

    private final Map<JustMockDefinition, String> beanNames;

    JustMockBindings(Map<JustMockDefinition, String> beanNames) {
        this.beanNames = Collections.unmodifiableMap(new LinkedHashMap<JustMockDefinition, String>(beanNames));
    }

    String getBeanName(JustMockDefinition definition) {
        if (definition == null) {
            return null;
        }
        String exact = beanNames.get(definition);
        if (exact != null) {
            return exact;
        }
        if (definition.isSpy() || !definition.isFieldDeclaration() || definition.hasQualifierAnnotations()) {
            return null;
        }
        String explicit = definition.getExplicitBeanName();
        String matched = null;
        for (Map.Entry<JustMockDefinition, String> entry : beanNames.entrySet()) {
            JustMockDefinition source = entry.getKey();
            if (source.isFieldDeclaration() || source.isSpy() || !isKnownThreadScopedSource(source)) {
                continue;
            }
            if (!typesCompatible(definition.getType(), source.getType())) {
                continue;
            }
            String beanName = entry.getValue();
            if (explicit != null && !explicit.isEmpty()
                    && !explicit.equals(beanName)
                    && !explicit.equals(source.getExplicitBeanName())) {
                continue;
            }
            if (matched != null && !matched.equals(beanName)) {
                throw new IllegalStateException("[JustMock] Multiple thread-scoped mocks match "
                        + definition.describe() + ". Candidates include '" + matched + "' and '"
                        + beanName + "'. Use @JustMock(name = \"...\") or @Qualifier. "
                        + "JustTest does not choose between them or auto-exclude auto-configurations.");
            }
            matched = beanName;
        }
        return matched;
    }

    private static boolean isKnownThreadScopedSource(JustMockDefinition source) {
        String label = source.sourceLabel();
        return "@ThreadScopedMock".equals(label)
                || JustTestProjectFeignAutoMock.SOURCE.equals(label);
    }

    private static boolean typesCompatible(Class<?> fieldType, Class<?> sourceType) {
        return fieldType.isAssignableFrom(sourceType);
    }
}

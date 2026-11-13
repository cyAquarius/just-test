package com.just.test.internal.mock;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.type.MethodMetadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 识别 mock / {@code *AutoConfiguration} 候选来源，并格式化 fail-fast 诊断。
 *
 * <p>自动配置来源只做 best-effort：工厂方法声明类、factory bean 类型、
 * BeanDefinition resource 描述。不加载第三方类，也不自动 exclude。</p>
 */
final class JustMockBeanOrigins {

    static final String SOURCE_ATTRIBUTE = "com.just.test.mockSource";
    static final String APPLICATION_BEAN = "application bean";

    private JustMockBeanOrigins() {
    }

    static List<Candidate> describeCandidates(ConfigurableListableBeanFactory beanFactory,
                                              Collection<String> beanNames,
                                              Map<String, String> mockSources) {
        Map<String, String> sources = mockSources == null
                ? Collections.<String, String>emptyMap()
                : mockSources;
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (String beanName : beanNames) {
            if (!isAutowireCandidate(beanFactory, beanName)) {
                continue;
            }
            String mockSource = resolveMockSource(beanFactory, beanName, sources);
            String autoConfiguration = mockSource != null
                    ? null
                    : detectAutoConfigurationClass(beanFactory, beanName);
            String origin;
            if (mockSource != null) {
                origin = mockSource;
            } else if (autoConfiguration != null) {
                origin = autoConfiguration;
            } else {
                origin = fallbackOrigin(beanFactory, beanName);
            }
            candidates.add(new Candidate(
                    beanName, origin, mockSource != null, autoConfiguration != null));
        }
        return candidates;
    }

    static boolean hasMockAndAutoConfiguration(List<Candidate> candidates) {
        if (candidates.size() <= 1) {
            return false;
        }
        boolean mock = false;
        boolean autoConfiguration = false;
        for (Candidate candidate : candidates) {
            if (candidate.mock) {
                mock = true;
            }
            if (candidate.autoConfiguration) {
                autoConfiguration = true;
            }
        }
        return mock && autoConfiguration;
    }

    static boolean hasAutoConfiguration(List<Candidate> candidates) {
        for (Candidate candidate : candidates) {
            if (candidate.autoConfiguration) {
                return true;
            }
        }
        return false;
    }

    static IllegalStateException conflict(Class<?> type, List<Candidate> candidates) {
        return new IllegalStateException(format(type, candidates));
    }

    static String format(Class<?> type, List<Candidate> candidates) {
        StringBuilder message = new StringBuilder();
        message.append("[JustTest] Conflicting beans of type ").append(type.getName()).append('.');
        message.append(" JustTest does not choose between a mock and another bean of the same type.");
        message.append(" Candidates:\n");
        Set<String> autoConfigurations = new LinkedHashSet<String>();
        for (Candidate candidate : candidates) {
            message.append("  - ").append(candidate.beanName)
                    .append(": ").append(candidate.origin).append('\n');
            if (candidate.autoConfiguration) {
                autoConfigurations.add(candidate.origin);
            }
        }
        message.append("Next steps:\n");
        if (!autoConfigurations.isEmpty()) {
            message.append("  - @JustTestProject(excludeAutoConfiguration = { ");
            boolean first = true;
            for (String autoConfiguration : autoConfigurations) {
                if (!first) {
                    message.append(", ");
                }
                first = false;
                message.append(classLiteral(autoConfiguration)).append(".class");
            }
            message.append(" })\n");
        }
        message.append("  - or remove the redundant @JustMock / @JustSpy / @ThreadScopedMock");
        return message.toString();
    }

    static IllegalStateException spyAndMockConflict(Class<?> type,
                                                    List<JustMockDefinition> spies,
                                                    List<JustMockDefinition> mocks) {
        return new IllegalStateException(formatSpyAndMock(type, spies, mocks));
    }

    static String formatSpyAndMock(Class<?> type,
                                   List<JustMockDefinition> spies,
                                   List<JustMockDefinition> mocks) {
        StringBuilder message = new StringBuilder();
        message.append("[JustTest] Conflicting beans of type ").append(type.getName()).append('.');
        message.append(" JustTest does not combine @JustSpy with @JustMock or @ThreadScopedMock");
        message.append(" for the same type. Candidates:\n");
        appendSpyMockCandidates(message, spies);
        appendSpyMockCandidates(message, mocks);
        message.append("Next steps:\n");
        message.append("  - keep only @JustSpy so unstubbed methods call the real bean\n");
        message.append("  - or remove @JustSpy and keep @JustMock / @ThreadScopedMock\n");
        return message.toString();
    }

    static IllegalStateException sameBeanSpyMockConflict(String beanName,
                                                         JustMockDefinition spy,
                                                         JustMockDefinition mock) {
        StringBuilder message = new StringBuilder();
        message.append("[JustTest] Conflicting beans named '").append(beanName).append("'.");
        message.append(" @JustSpy and ").append(mock.sourceLabel()).append(" selected the same bean.");
        message.append(" Candidates:\n");
        message.append("  - ").append(beanName).append(": ").append(spy.sourceLabel())
                .append(" from ").append(spy.describe()).append('\n');
        message.append("  - ").append(beanName).append(": ").append(mock.sourceLabel())
                .append(" from ").append(mock.describe()).append('\n');
        message.append("Next steps:\n");
        message.append("  - keep only @JustSpy so unstubbed methods call the real bean\n");
        message.append("  - or remove @JustSpy and keep ").append(mock.sourceLabel()).append('\n');
        return new IllegalStateException(message.toString());
    }

    private static void appendSpyMockCandidates(StringBuilder message, List<JustMockDefinition> definitions) {
        for (JustMockDefinition definition : definitions) {
            String name = definition.getExplicitBeanName();
            if (name == null || name.isEmpty()) {
                name = definition.getFieldName();
            }
            message.append("  - ").append(name).append(": ").append(definition.sourceLabel())
                    .append(" from ").append(definition.describe()).append('\n');
        }
    }

    static String detectAutoConfigurationClass(ConfigurableListableBeanFactory beanFactory,
                                               String beanName) {
        if (beanFactory == null || beanName == null || !beanFactory.containsBeanDefinition(beanName)) {
            return null;
        }
        BeanDefinition definition = beanFactory.getBeanDefinition(beanName);
        String fromFactoryMethod = factoryMethodDeclaringClass(definition);
        if (isAutoConfigurationClassName(fromFactoryMethod)) {
            return fromFactoryMethod;
        }
        String factoryBeanName = definition.getFactoryBeanName();
        if (factoryBeanName != null && !factoryBeanName.equals(beanName)) {
            Class<?> factoryType = safeType(beanFactory, factoryBeanName);
            if (factoryType != null && isAutoConfigurationClassName(factoryType.getName())) {
                return factoryType.getName();
            }
            if (beanFactory.containsBeanDefinition(factoryBeanName)) {
                BeanDefinition factoryDefinition = beanFactory.getBeanDefinition(factoryBeanName);
                if (isAutoConfigurationClassName(factoryDefinition.getBeanClassName())) {
                    return factoryDefinition.getBeanClassName();
                }
                String factoryResource = classNameFromResource(factoryDefinition.getResourceDescription());
                if (isAutoConfigurationClassName(factoryResource)) {
                    return factoryResource;
                }
            }
        }
        String fromResource = classNameFromResource(definition.getResourceDescription());
        if (isAutoConfigurationClassName(fromResource)) {
            return fromResource;
        }
        if (isAutoConfigurationClassName(definition.getBeanClassName())) {
            return definition.getBeanClassName();
        }
        return null;
    }

    static boolean isAutoConfigurationClassName(String className) {
        if (className == null || className.isEmpty()) {
            return false;
        }
        return simpleName(className).endsWith("AutoConfiguration");
    }

    static String classNameFromResource(String resourceDescription) {
        if (resourceDescription == null || resourceDescription.isEmpty()) {
            return null;
        }
        int classSuffix = resourceDescription.lastIndexOf(".class");
        if (classSuffix < 0) {
            return null;
        }
        int bang = resourceDescription.lastIndexOf('!', classSuffix);
        int bracket = resourceDescription.lastIndexOf('[', classSuffix);
        int start = Math.max(bang, bracket) + 1;
        String path = resourceDescription.substring(start, classSuffix).trim();
        path = path.replace('\\', '/');
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.isEmpty()) {
            return null;
        }
        return path.replace('/', '.');
    }

    /**
     * 诊断里可编译的类字面量。嵌套类保留外部类名，{@code com.acme.Outer$SampleLogAutoConfiguration}
     * 写成 {@code Outer.SampleLogAutoConfiguration}，而不是被截成无法编译的 {@code SampleLogAutoConfiguration}。
     */
    static String classLiteral(String className) {
        if (className == null || className.isEmpty()) {
            return className;
        }
        int lastDot = className.lastIndexOf('.');
        String simple = lastDot < 0 ? className : className.substring(lastDot + 1);
        return simple.replace('$', '.');
    }

    static String simpleName(String className) {
        if (className == null || className.isEmpty()) {
            return className;
        }
        int lastDot = className.lastIndexOf('.');
        String simple = lastDot < 0 ? className : className.substring(lastDot + 1);
        int nested = simple.lastIndexOf('$');
        return nested < 0 ? simple : simple.substring(nested + 1);
    }

    private static String resolveMockSource(ConfigurableListableBeanFactory beanFactory,
                                            String beanName,
                                            Map<String, String> mockSources) {
        String mapped = mockSources.get(beanName);
        if (mapped != null) {
            return mapped;
        }
        if (!beanFactory.containsBeanDefinition(beanName)) {
            return null;
        }
        Object attribute = beanFactory.getBeanDefinition(beanName).getAttribute(SOURCE_ATTRIBUTE);
        return attribute instanceof String ? (String) attribute : null;
    }

    private static boolean isAutowireCandidate(ConfigurableListableBeanFactory beanFactory,
                                               String beanName) {
        if (!beanFactory.containsBeanDefinition(beanName)) {
            return true;
        }
        return beanFactory.getBeanDefinition(beanName).isAutowireCandidate();
    }

    private static String fallbackOrigin(ConfigurableListableBeanFactory beanFactory, String beanName) {
        if (!beanFactory.containsBeanDefinition(beanName)) {
            return APPLICATION_BEAN;
        }
        BeanDefinition definition = beanFactory.getBeanDefinition(beanName);
        String factoryMethod = factoryMethodDeclaringClass(definition);
        if (factoryMethod != null && !factoryMethod.isEmpty()) {
            return factoryMethod;
        }
        String fromResource = classNameFromResource(definition.getResourceDescription());
        if (fromResource != null && !fromResource.isEmpty()) {
            return fromResource;
        }
        if (definition.getBeanClassName() != null && !definition.getBeanClassName().isEmpty()) {
            return definition.getBeanClassName();
        }
        return APPLICATION_BEAN;
    }

    private static String factoryMethodDeclaringClass(BeanDefinition definition) {
        if (!(definition instanceof AnnotatedBeanDefinition)) {
            return null;
        }
        MethodMetadata metadata = ((AnnotatedBeanDefinition) definition).getFactoryMethodMetadata();
        return metadata == null ? null : metadata.getDeclaringClassName();
    }

    private static Class<?> safeType(ConfigurableListableBeanFactory beanFactory, String beanName) {
        try {
            return beanFactory.getType(beanName, false);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    static final class Candidate {
        final String beanName;
        final String origin;
        final boolean mock;
        final boolean autoConfiguration;

        Candidate(String beanName, String origin, boolean mock, boolean autoConfiguration) {
            this.beanName = beanName;
            this.origin = origin;
            this.mock = mock;
            this.autoConfiguration = autoConfiguration;
        }
    }
}

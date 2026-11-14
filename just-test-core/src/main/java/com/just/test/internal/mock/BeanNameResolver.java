package com.just.test.internal.mock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.ResolvableType;
import org.springframework.core.type.MethodMetadata;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 从 BeanFactory 解析 mock / spy 的目标 Bean 名和产品类型。
 *
 * <p>同包实现，不是公开 API。</p>
 */
class BeanNameResolver {

    // 日志类别保持 JustMockPostProcessor，拆分不改变现有日志名。
    private static final Logger log = LoggerFactory.getLogger(JustMockPostProcessor.class);

    Class<?> resolveMockType(ConfigurableListableBeanFactory beanFactory,
                             BeanDefinitionRegistry registry,
                             String beanName,
                             JustMockDefinition definition) {
        if (!registry.containsBeanDefinition(beanName) && !beanFactory.containsSingleton(beanName)) {
            return definition.getType();
        }
        Class<?> targetType = safeBeanType(beanFactory, beanName);
        if (targetType == null || !definition.getType().isAssignableFrom(targetType)) {
            return definition.getType();
        }
        return targetType;
    }

    private static boolean isInternalBeanName(String candidate) {
        return candidate.startsWith(JustMockPostProcessor.SCOPED_TARGET_PREFIX)
                || candidate.startsWith(JustMockPostProcessor.SPY_TARGET_PREFIX)
                || candidate.startsWith(JustMockPostProcessor.INTERCEPT_TARGET_PREFIX);
    }

    String resolveBeanName(ConfigurableListableBeanFactory beanFactory,
                           BeanDefinitionRegistry registry,
                           JustMockDefinition definition) {
        LinkedHashSet<String> logicalCandidates =
                collectLogicalCandidates(beanFactory, definition.getType(), false);
        if (!definition.getExplicitBeanName().isEmpty()) {
            String existingBeanName = findExistingBeanDefinition(
                    registry, beanFactory, definition.getExplicitBeanName());
            if (existingBeanName != null
                    && isTypeCompatible(beanFactory, existingBeanName, definition)) {
                return existingBeanName;
            }
            if (definition.isCreateIfAbsent()) {
                return resolveCreateIfAbsent(beanFactory, definition, logicalCandidates);
            }
            if (logicalCandidates.isEmpty()) {
                logicalCandidates.addAll(
                        collectLogicalCandidates(beanFactory, definition.getType(), true));
            }
            return requireCandidate(beanFactory, definition, logicalCandidates,
                    definition.getExplicitBeanName());
        }

        DependencyDescriptor descriptor = definition.toDependencyDescriptor();
        if (logicalCandidates.isEmpty()) {
            String fallback = resolveNamedFallback(beanFactory, registry, definition, descriptor);
            if (fallback != null) {
                return fallback;
            }
            // FactoryBean object types are not always available without eager initialization.
            // The name lookup above keeps this fallback independent of eager-init side effects.
            logicalCandidates.addAll(
                    collectLogicalCandidates(beanFactory, definition.getType(), true));
        }

        return resolveFromTypeCandidates(beanFactory, registry, definition,
                descriptor, logicalCandidates);
    }

    LinkedHashSet<String> collectLogicalCandidates(
            ConfigurableListableBeanFactory beanFactory, Class<?> type, boolean allowEagerInit) {
        String[] candidates = beanFactory.getBeanNamesForType(type, true, allowEagerInit);
        LinkedHashSet<String> logicalCandidates = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (!isInternalBeanName(candidate)) {
                logicalCandidates.add(candidate);
            }
        }
        if (!allowEagerInit) {
            // Read product metadata without constructing FactoryBeans (notably Feign clients).
            // Always union candidates: a visible bean must not hide a second mapper (#65).
            for (String name : beanFactory.getBeanDefinitionNames()) {
                if (!isInternalBeanName(name)) {
                    Class<?> productType = factoryProductType(beanFactory,
                            beanFactory.getMergedBeanDefinition(name));
                    if (productType != null && type.isAssignableFrom(productType)) {
                        logicalCandidates.add(name);
                    }
                }
            }
        }
        return logicalCandidates;
    }

    private static Class<?> factoryProductType(ConfigurableListableBeanFactory factory,
                                             BeanDefinition definition) {
        if (definition.isAbstract()) {
            return null;
        }
        Class<?> objectType = metadataClass(factory, definition.getAttribute("factoryBeanObjectType"));
        if (objectType != null) {
            return objectType;
        }
        Class<?> beanClass = metadataClass(factory, definition.getBeanClassName());
        // Avoid a core dependency on mybatis-spring; also accept MapperFactoryBean subclasses.
        while (beanClass != null) {
            if ("org.mybatis.spring.mapper.MapperFactoryBean".equals(beanClass.getName())) {
                PropertyValue property =
                        definition.getPropertyValues().getPropertyValue("mapperInterface");
                if (property != null) {
                    return metadataClass(factory, property.getValue());
                }
                ConstructorArgumentValues arguments =
                        definition.getConstructorArgumentValues();
                ConstructorArgumentValues.ValueHolder indexed =
                        arguments.getIndexedArgumentValue(0, null);
                if (indexed != null) {
                    return metadataClass(factory, indexed.getValue());
                }
                if (arguments.getGenericArgumentValues().size() == 1) {
                    return metadataClass(factory, arguments.getGenericArgumentValues().get(0).getValue());
                }
                return null;
            }
            beanClass = beanClass.getSuperclass();
        }
        return null;
    }

    private static Class<?> metadataClass(ConfigurableListableBeanFactory factory, Object value) {
        if (value instanceof Class<?>) {
            return (Class<?>) value;
        }
        if (value instanceof ResolvableType) {
            return ((ResolvableType) value).resolve();
        }
        if (value instanceof TypedStringValue) {
            value = ((TypedStringValue) value).getValue();
        }
        if (value instanceof String) {
            try {
                return Class.forName((String) value, false, factory.getBeanClassLoader());
            } catch (ClassNotFoundException | LinkageError ex) {
                return null;
            }
        }
        return null;
    }

    private String resolveCreateIfAbsent(ConfigurableListableBeanFactory beanFactory,
                                         JustMockDefinition definition,
                                         Set<String> logicalCandidates) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>(logicalCandidates);
        if (candidates.isEmpty()) {
            // 不 eager-init：避免实例化真实 FeignClientFactoryBean（可能缺 LoadBalancer）
            candidates.addAll(collectLogicalCandidates(beanFactory, definition.getType(), false));
        }
        if (candidates.size() == 1) {
            return candidates.iterator().next();
        }
        if (candidates.isEmpty()) {
            return definition.getExplicitBeanName();
        }
        for (String candidate : candidates) {
            if (matchesName(beanFactory, candidate, definition.getExplicitBeanName())) {
                return candidate;
            }
        }
        throw ambiguous(beanFactory, definition, candidates);
    }

    private String resolveFromTypeCandidates(ConfigurableListableBeanFactory beanFactory,
                                             BeanDefinitionRegistry registry,
                                             JustMockDefinition definition,
                                             DependencyDescriptor descriptor,
                                             Set<String> logicalCandidates) {
        if (!definition.getExplicitBeanName().isEmpty()) {
            return requireCandidate(beanFactory, definition, logicalCandidates,
                    definition.getExplicitBeanName());
        }

        LinkedHashSet<String> injectableCandidates = new LinkedHashSet<>();
        for (String candidate : logicalCandidates) {
            if (beanFactory.isAutowireCandidate(candidate, descriptor)) {
                injectableCandidates.add(candidate);
            }
        }

        if (injectableCandidates.isEmpty()) {
            if (definition.hasQualifierAnnotations()) {
                throw new IllegalStateException(label(definition) + " No autowire candidate matches the qualifier for "
                        + definition.describe() + ". Type candidates: " + logicalCandidates);
            }
            String fallback = resolveNamedFallback(beanFactory, registry, definition, descriptor);
            if (fallback != null) {
                return fallback;
            }
            if (definition.isSpy()) {
                throw missingSpyBean(definition, logicalCandidates);
            }
            String generated = generateAvailableBeanName(
                    definition.getType(), beanFactory, registry);
            log.warn("[JustMock] No injectable bean found for {}, registering '{}'", definition.describe(), generated);
            return generated;
        }
        String primary = null;
        for (String candidate : injectableCandidates) {
            if (beanFactory.containsBeanDefinition(candidate)
                    && beanFactory.getBeanDefinition(candidate).isPrimary()) {
                if (primary != null) {
                    throw ambiguous(beanFactory, definition, injectableCandidates);
                }
                primary = candidate;
            }
        }
        if (primary != null) return primary;
        for (String candidate : injectableCandidates) {
            if (matchesName(beanFactory, candidate, definition.getFieldName())) {
                return candidate;
            }
        }
        if (injectableCandidates.size() == 1) return injectableCandidates.iterator().next();
        throw ambiguous(beanFactory, definition, injectableCandidates);
    }

    private String resolveNamedFallback(ConfigurableListableBeanFactory beanFactory,
                                        BeanDefinitionRegistry registry,
                                        JustMockDefinition definition,
                                        DependencyDescriptor descriptor) {
        String fieldName = definition.getFieldName();
        String fieldBeanName = findExistingBeanDefinition(registry, beanFactory, fieldName);
        if (fieldBeanName != null
                && beanFactory.isAutowireCandidate(fieldBeanName, descriptor)
                && isTypeCompatible(beanFactory, fieldBeanName, definition)) {
            log.warn("[JustMock] Type scan did not find a candidate for {}; "
                            + "using field-name fallback '{}' (resolved bean '{}')",
                    definition.describe(), fieldName, fieldBeanName);
            return fieldBeanName;
        }

        String defaultBeanName = generateBeanName(definition.getType());
        if (!defaultBeanName.equals(fieldName)) {
            String defaultBean = findExistingBeanDefinition(registry, beanFactory, defaultBeanName);
            if (defaultBean != null
                    && beanFactory.isAutowireCandidate(defaultBean, descriptor)
                    && isTypeCompatible(beanFactory, defaultBean, definition)) {
                log.warn("[JustMock] Type scan did not find a candidate for {}; "
                                + "using default bean-name fallback '{}' (resolved bean '{}')",
                        definition.describe(), defaultBeanName, defaultBean);
                return defaultBean;
            }
        }
        return null;
    }

    private boolean isTypeCompatible(ConfigurableListableBeanFactory beanFactory,
                                     String beanName, JustMockDefinition definition) {
        Class<?> beanType = safeBeanType(beanFactory, beanName);
        return beanType == null || definition.getType().isAssignableFrom(beanType);
    }

    /**
     * FeignClientFactoryBean 等类型可能不在测试 classpath；eager class-load 会抛
     * {@code CannotLoadBeanClassException}，这里按“类型未知”处理以免挡住替换。
     */
    static Class<?> safeBeanType(ConfigurableListableBeanFactory beanFactory, String beanName) {
        try {
            return beanFactory.getType(beanName, false);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String findExistingBeanDefinition(BeanDefinitionRegistry registry,
                                              ConfigurableListableBeanFactory beanFactory,
                                              String requestedName) {
        for (String candidate : registry.getBeanDefinitionNames()) {
            if (!isInternalBeanName(candidate)
                    && matchesName(beanFactory, candidate, requestedName)) {
                return candidate;
            }
        }
        return null;
    }

    private String requireCandidate(ConfigurableListableBeanFactory beanFactory,
                                    JustMockDefinition definition, Set<String> candidates,
                                    String requestedName) {
        for (String candidate : candidates) {
            if (matchesName(beanFactory, candidate, requestedName)) {
                return candidate;
            }
        }
        throw new IllegalStateException(label(definition) + " No bean named '" + requestedName + "' for "
                + definition.describe() + ". Candidates: " + candidates);
    }

    private boolean matchesName(ConfigurableListableBeanFactory beanFactory,
                                String candidate, String requestedName) {
        if (candidate.equals(requestedName)) {
            return true;
        }
        if (beanFactory == null) {
            return false;
        }
        for (String alias : beanFactory.getAliases(candidate)) {
            if (alias.equals(requestedName)) {
                return true;
            }
        }
        return false;
    }

    private IllegalStateException ambiguous(ConfigurableListableBeanFactory beanFactory,
                                            JustMockDefinition definition, Set<String> candidates) {
        List<JustMockBeanOrigins.Candidate> described = JustMockBeanOrigins.describeCandidates(
                beanFactory, candidates, Collections.<String, String>emptyMap());
        if (JustMockBeanOrigins.hasAutoConfiguration(described)) {
            return JustMockBeanOrigins.conflict(definition.getType(), described);
        }
        String annotation = definition.isSpy() ? "@JustSpy" : "@JustMock";
        return new IllegalStateException(label(definition) + " Multiple beans found for " + definition.describe()
                + ": " + candidates + ". Use " + annotation + "(name = \"...\") or @Qualifier.");
    }

    private static String label(JustMockDefinition definition) {
        return definition.isSpy() ? "[JustSpy]" : "[JustMock]";
    }

    private static IllegalStateException missingSpyBean(JustMockDefinition definition, Set<String> candidates) {
        return new IllegalStateException("[JustSpy] No existing bean found for " + definition.describe()
                + ". @JustSpy only wraps a bean that is already defined and does not create one."
                + " Type candidates: " + candidates);
    }

    private String generateBeanName(Class<?> type) {
        return JustMockDefinition.decapitalize(type.getSimpleName());
    }

    /**
     * {@code @ThreadScopedMock} 的产品类型。优先 Bean 定义上已经写明的类型；
     * 工厂方法返回普通类时用声明的返回类型；返回 {@link FactoryBean} 时解析泛型产品类型。
     * 仍然未知时才 {@code getType(name, false)}，不初始化 FactoryBean。
     */
    Class<?> threadScopedMockType(ConfigurableListableBeanFactory beanFactory,
                                  String beanName,
                                  BeanDefinition definition) {
        Class<?> declared = declaredThreadScopedType(beanFactory, definition);
        if (declared != null) {
            return declared;
        }
        return safeBeanType(beanFactory, beanName);
    }

    private Class<?> declaredThreadScopedType(ConfigurableListableBeanFactory beanFactory,
                                              BeanDefinition definition) {
        Class<?> product = factoryProductType(beanFactory, definition);
        if (product != null) {
            return product;
        }
        if (!(definition instanceof AnnotatedBeanDefinition)) {
            return null;
        }
        MethodMetadata metadata = ((AnnotatedBeanDefinition) definition).getFactoryMethodMetadata();
        if (metadata == null) {
            return null;
        }
        Class<?> returnType = metadataClass(beanFactory, metadata.getReturnTypeName());
        if (returnType == null || returnType == void.class || returnType.isPrimitive()
                || returnType == Object.class) {
            return null;
        }
        if (FactoryBean.class.isAssignableFrom(returnType)) {
            return factoryBeanProduct(beanFactory, metadata);
        }
        return returnType;
    }

    private static Class<?> factoryBeanProduct(ConfigurableListableBeanFactory beanFactory,
                                               MethodMetadata metadata) {
        Class<?> declaring = metadataClass(beanFactory, metadata.getDeclaringClassName());
        if (declaring == null) {
            return null;
        }
        String returnTypeName = metadata.getReturnTypeName();
        Class<?> product = null;
        for (Method method : declaring.getDeclaredMethods()) {
            if (method.isBridge() || method.isSynthetic()
                    || !method.getName().equals(metadata.getMethodName())
                    || !returnTypeName.equals(method.getReturnType().getName())) {
                continue;
            }
            Class<?> candidate = ResolvableType.forMethodReturnType(method)
                    .as(FactoryBean.class)
                    .getGeneric(0)
                    .resolve();
            if (candidate == null || candidate == Object.class) {
                continue;
            }
            if (product != null && product != candidate) {
                return null;
            }
            product = candidate;
        }
        return product;
    }

    private String generateAvailableBeanName(Class<?> type,
                                             ConfigurableListableBeanFactory beanFactory,
                                             BeanDefinitionRegistry registry) {
        String baseName = generateBeanName(type);
        if (!registry.containsBeanDefinition(baseName)
                && !registry.isAlias(baseName)
                && !beanFactory.containsSingleton(baseName)) {
            return baseName;
        }
        return BeanDefinitionReaderUtils.uniqueBeanName(baseName, registry);
    }
}

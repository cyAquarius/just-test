package com.just.test.internal.mock;

import com.just.test.annotation.ThreadScopedMock;
import com.just.test.internal.project.JustTestProjectFeignAutoMock;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.core.type.MethodMetadata;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 将 {@code @JustMock} 声明的类型 + {@code @ThreadScopedMock} 标记的 @Bean 方法
 * 统一替换为 thread-scoped + ScopedProxy 的 mock bean。
 *
 * <p>对每个 mock 类型：找到现有 bean → 移除 → 注册 thread-scoped mock → 包装 ScopedProxy。
 * 原 bean 名称指向 proxy，每次方法调用委托给当前线程的 mock 实例。</p>
 *
 * <p>来源：
 * <ol>
 *   <li>{@code mockTypes} —— 测试类字段 {@code @JustMock} 扫描所得（per-test 差异化 mock）</li>
 *   <li>{@code @ThreadScopedMock} —— Configuration 类 {@code @Bean} 方法标记（全局外部依赖 mock）</li>
 *   <li>opt-in {@code @JustTestProject(autoMockFeignClients = true)} 发现的 {@code @FeignClient}
 *       接口；显式 {@code @JustMock} / {@code @ThreadScopedMock} 优先</li>
 * </ol>
 * </p>
 */
class JustMockPostProcessor implements BeanFactoryPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(JustMockPostProcessor.class);
    private static final String SCOPED_TARGET_PREFIX = "scopedTarget.";

    private final Set<JustMockDefinition> mockDefinitions;

    JustMockPostProcessor(Set<JustMockDefinition> mockDefinitions) {
        this.mockDefinitions = mockDefinitions;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        if (!(beanFactory instanceof BeanDefinitionRegistry)) {
            log.warn("[JustMock] BeanFactory is not a BeanDefinitionRegistry, skipping");
            return;
        }
        BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;

        // 合并 @JustMock 字段类型 + @ThreadScopedMock @Bean 方法返回类型，再叠加 opt-in Feign
        Set<JustMockDefinition> allMockDefinitions = new LinkedHashSet<>(mockDefinitions);
        allMockDefinitions.addAll(collectThreadScopedMockDefinitions(beanFactory));
        allMockDefinitions.addAll(collectAutoMockedFeignClients(beanFactory, registry, allMockDefinitions));

        Set<String> registeredBeanNames = new LinkedHashSet<>();
        java.util.Map<JustMockDefinition, String> bindings = new java.util.LinkedHashMap<>();
        for (JustMockDefinition definition : allMockDefinitions) {
            String beanName = registerThreadScopedMock(beanFactory, registry, definition);
            if (beanName != null) {
                registeredBeanNames.add(beanName);
                bindings.put(definition, beanName);
            }
        }

        registerBindingsBean(registry, bindings);
        // 注册 Registry bean，供 JustTestExtension 在 case 开始时预热所有 thread-scoped mock
        registerRegistryBean(registry, registeredBeanNames);
    }

    private void registerBindingsBean(BeanDefinitionRegistry registry,
                                      java.util.Map<JustMockDefinition, String> bindings) {
        BeanDefinition definition = BeanDefinitionBuilder
                .genericBeanDefinition(JustMockBindings.class,
                        () -> new JustMockBindings(bindings))
                .getBeanDefinition();
        registry.registerBeanDefinition("justMockBindings", definition);
    }

    private void registerRegistryBean(BeanDefinitionRegistry registry, Set<String> beanNames) {
        Set<String> snapshot = new LinkedHashSet<>(beanNames);
        BeanDefinition registryDef = BeanDefinitionBuilder
                .genericBeanDefinition(ThreadScopedMockRegistry.class,
                        () -> new ThreadScopedMockRegistry(snapshot))
                .getBeanDefinition();
        registry.registerBeanDefinition("justMockThreadScopedMockRegistry", registryDef);
        log.info("[JustMock] Registered ThreadScopedMockRegistry with {} beans", snapshot.size());
    }

    /**
     * 扫描所有 BeanDefinition，找出工厂方法带 {@link ThreadScopedMock} 注解的类型。
     *
     * <p>Spring 处理 {@code @Configuration} 时，每个 {@code @Bean} 方法会产生
     * {@code AnnotatedBeanDefinition}，通过 {@code getFactoryMethodMetadata()} 可读取方法级注解。</p>
     */
    private Set<JustMockDefinition> collectThreadScopedMockDefinitions(ConfigurableListableBeanFactory beanFactory) {
        Set<JustMockDefinition> definitions = new LinkedHashSet<>();
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition bd = beanFactory.getBeanDefinition(beanName);
            if (!(bd instanceof AnnotatedBeanDefinition)) {
                continue;
            }
            MethodMetadata factoryMethod = ((AnnotatedBeanDefinition) bd).getFactoryMethodMetadata();
            if (factoryMethod == null) {
                continue;
            }
            if (!factoryMethod.isAnnotated(ThreadScopedMock.class.getName())) {
                continue;
            }
            Class<?> type = beanFactory.getType(beanName);
            if (type == null) {
                log.warn("[JustMock] @ThreadScopedMock bean '{}' has null type, skipping", beanName);
                continue;
            }
            definitions.add(JustMockDefinition.forBeanName(type, beanName, "@ThreadScopedMock"));
            log.debug("[JustMock] Discovered @ThreadScopedMock bean '{}' of type {}", beanName, type.getSimpleName());
        }
        return definitions;
    }

    private Set<JustMockDefinition> collectAutoMockedFeignClients(
            ConfigurableListableBeanFactory beanFactory,
            BeanDefinitionRegistry registry,
            Set<JustMockDefinition> alreadyMocked) {
        if (!registry.containsBeanDefinition(JustTestProjectFeignAutoMock.BEAN_NAME)
                && !beanFactory.containsSingleton(JustTestProjectFeignAutoMock.BEAN_NAME)) {
            return Collections.emptySet();
        }
        JustTestProjectFeignAutoMock settings = beanFactory.getBean(
                JustTestProjectFeignAutoMock.BEAN_NAME, JustTestProjectFeignAutoMock.class);
        Set<Class<?>> mockedTypes = mockedTypes(alreadyMocked);
        Set<JustMockDefinition> definitions = new LinkedHashSet<>();
        for (Map.Entry<Class<?>, String> entry : settings.discover(beanFactory).entrySet()) {
            Class<?> type = entry.getKey();
            if (mockedTypes.contains(type)) {
                log.debug("[JustMock] Skipping auto @FeignClient {} because an explicit mock already exists",
                        type.getName());
                continue;
            }
            JustMockDefinition definition = JustMockDefinition.forAutoMock(
                    type, entry.getValue(), JustTestProjectFeignAutoMock.SOURCE);
            definitions.add(definition);
            mockedTypes.add(type);
            log.info("[JustMock] Auto-mocking @FeignClient {}", type.getName());
        }
        return definitions;
    }

    private static Set<Class<?>> mockedTypes(Set<JustMockDefinition> definitions) {
        Set<Class<?>> types = new LinkedHashSet<>();
        for (JustMockDefinition definition : definitions) {
            types.add(definition.getType());
        }
        return types;
    }

    private String registerThreadScopedMock(ConfigurableListableBeanFactory beanFactory,
                                            BeanDefinitionRegistry registry,
                                            JustMockDefinition definition) {
        String targetBeanName = resolveBeanName(beanFactory, registry, definition);
        Class<?> mockType = resolveMockType(beanFactory, registry, targetBeanName, definition);
        BeanDefinition originalDefinition = null;
        BeanDefinition originalScopedTarget = null;
        if (registry.containsBeanDefinition(targetBeanName)) {
            originalDefinition = registry.getBeanDefinition(targetBeanName);
            registry.removeBeanDefinition(targetBeanName);
            String scopedTargetName = ScopedProxyUtils.getTargetBeanName(targetBeanName);
            if (registry.containsBeanDefinition(scopedTargetName)) {
                originalScopedTarget = registry.getBeanDefinition(scopedTargetName);
                registry.removeBeanDefinition(scopedTargetName);
            }
            log.debug("[JustMock] Removed existing bean definition: {}", targetBeanName);
        }

        // 注册 thread-scoped mock bean
        AbstractBeanDefinition mockDef = createMockBeanDefinition(mockType);
        copyAutowireMetadata(mockDef, originalDefinition, originalScopedTarget);
        BeanDefinitionHolder holder = new BeanDefinitionHolder(mockDef, targetBeanName);
        BeanDefinitionHolder proxy = ScopedProxyUtils.createScopedProxy(holder, registry, true);
        if (proxy.getBeanDefinition() instanceof AbstractBeanDefinition) {
            copyAutowireMetadata((AbstractBeanDefinition) proxy.getBeanDefinition(),
                    originalDefinition, originalScopedTarget);
        }
        registry.registerBeanDefinition(targetBeanName, proxy.getBeanDefinition());

        log.info("[JustMock] Registered thread-scoped mock for {} as '{}'",
                mockType.getSimpleName(), targetBeanName);
        return targetBeanName;
    }

    private Class<?> resolveMockType(ConfigurableListableBeanFactory beanFactory,
                                     BeanDefinitionRegistry registry,
                                     String beanName,
                                     JustMockDefinition definition) {
        if (!registry.containsBeanDefinition(beanName) && !beanFactory.containsSingleton(beanName)) {
            return definition.getType();
        }
        Class<?> targetType = beanFactory.getType(beanName, false);
        if (targetType == null || !definition.getType().isAssignableFrom(targetType)) {
            return definition.getType();
        }
        return targetType;
    }

    private String resolveBeanName(ConfigurableListableBeanFactory beanFactory,
                                   BeanDefinitionRegistry registry,
                                   JustMockDefinition definition) {
        java.util.LinkedHashSet<String> logicalCandidates =
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

    private java.util.LinkedHashSet<String> collectLogicalCandidates(
            ConfigurableListableBeanFactory beanFactory, Class<?> type, boolean allowEagerInit) {
        String[] candidates = beanFactory.getBeanNamesForType(type, true, allowEagerInit);
        java.util.LinkedHashSet<String> logicalCandidates = new java.util.LinkedHashSet<>();
        for (String candidate : candidates) {
            if (!candidate.startsWith(SCOPED_TARGET_PREFIX)) {
                logicalCandidates.add(candidate);
            }
        }
        return logicalCandidates;
    }

    private String resolveCreateIfAbsent(ConfigurableListableBeanFactory beanFactory,
                                         JustMockDefinition definition,
                                         Set<String> logicalCandidates) {
        java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>(logicalCandidates);
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
        throw ambiguous(definition, candidates);
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

        java.util.LinkedHashSet<String> injectableCandidates = new java.util.LinkedHashSet<>();
        for (String candidate : logicalCandidates) {
            if (beanFactory.isAutowireCandidate(candidate, descriptor)) {
                injectableCandidates.add(candidate);
            }
        }

        if (injectableCandidates.isEmpty()) {
            if (definition.hasQualifierAnnotations()) {
                throw new IllegalStateException("[JustMock] No autowire candidate matches the qualifier for "
                        + definition.describe() + ". Type candidates: " + logicalCandidates);
            }
            String fallback = resolveNamedFallback(beanFactory, registry, definition, descriptor);
            if (fallback != null) {
                return fallback;
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
                    throw ambiguous(definition, injectableCandidates);
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
        throw ambiguous(definition, injectableCandidates);
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
        Class<?> beanType = beanFactory.getType(beanName, false);
        return beanType == null || definition.getType().isAssignableFrom(beanType);
    }

    private String findExistingBeanDefinition(BeanDefinitionRegistry registry,
                                              ConfigurableListableBeanFactory beanFactory,
                                              String requestedName) {
        for (String candidate : registry.getBeanDefinitionNames()) {
            if (!candidate.startsWith(SCOPED_TARGET_PREFIX)
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
        throw new IllegalStateException("[JustMock] No bean named '" + requestedName + "' for "
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

    private IllegalStateException ambiguous(JustMockDefinition definition, Set<String> candidates) {
        return new IllegalStateException("[JustMock] Multiple beans found for " + definition.describe()
                + ": " + candidates + ". Use @JustMock(name = \"...\") or @Qualifier.");
    }

    private String generateBeanName(Class<?> type) {
        String simpleName = type.getSimpleName();
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
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

    @SuppressWarnings("unchecked")
    private <T> AbstractBeanDefinition createMockBeanDefinition(Class<T> mockType) {
        return BeanDefinitionBuilder
                .genericBeanDefinition(mockType, () -> Mockito.mock(mockType))
                .setScope("thread")
                .getBeanDefinition();
    }

    private void copyAutowireMetadata(AbstractBeanDefinition target,
                                      BeanDefinition logicalDefinition,
                                      BeanDefinition scopedTargetDefinition) {
        if (logicalDefinition != null) {
            target.setPrimary(logicalDefinition.isPrimary());
            target.setAutowireCandidate(logicalDefinition.isAutowireCandidate());
            copyQualifiers(target, logicalDefinition);
        }
        copyQualifiers(target, scopedTargetDefinition);
    }

    private void copyQualifiers(AbstractBeanDefinition target, BeanDefinition source) {
        if (source instanceof AbstractBeanDefinition) {
            target.copyQualifiersFrom((AbstractBeanDefinition) source);
        }
    }
}

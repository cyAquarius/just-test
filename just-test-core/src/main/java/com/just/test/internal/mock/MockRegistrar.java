package com.just.test.internal.mock;

import com.just.test.internal.project.JustTestProjectFeignAutoMock;
import org.mockito.AdditionalAnswers;
import org.mockito.MockSettings;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 注册 thread-scoped mock、spy，以及 Feign auto-mock 定义。
 *
 * <p>同包实现，不是公开 API。</p>
 */
class MockRegistrar {

    // 日志类别保持 JustMockPostProcessor，拆分不改变现有日志名。
    private static final Logger log = LoggerFactory.getLogger(JustMockPostProcessor.class);

    private final BeanNameResolver names;

    MockRegistrar(BeanNameResolver names) {
        this.names = names;
    }

    void registerMockDefinitions(ConfigurableListableBeanFactory beanFactory,
                                 BeanDefinitionRegistry registry,
                                 Iterable<JustMockDefinition> definitions,
                                 Map<String, JustMockDefinition> spiesByBeanName,
                                 Map<JustMockDefinition, String> bindings,
                                 Set<String> registeredBeanNames) {
        for (JustMockDefinition definition : definitions) {
            String beanName = registerThreadScopedMock(beanFactory, registry, definition, spiesByBeanName);
            if (beanName != null) {
                registeredBeanNames.add(beanName);
                bindings.put(definition, beanName);
                markMockSource(registry, beanName, definition);
            }
        }
    }

    /**
     * Feign 客户端还没注册成 Bean 时，auto-mock 会按简单类名创建它。
     * 这种 {@code @JustMock} 直接绑定，避免先走“找不到 Bean、准备新建”的告警。
     */
    String pendingFeignBind(JustMockDefinition fieldMock,
                            Map<Class<?>, String> feignDiscovered,
                            Set<Class<?>> feignBlocked) {
        if (feignDiscovered == null || feignDiscovered.isEmpty()
                || feignBlocked.contains(fieldMock.getType())
                || !feignDiscovered.containsKey(fieldMock.getType())
                || fieldMock.hasQualifierAnnotations()
                || !fieldMock.getExplicitBeanName().isEmpty()) {
            return null;
        }
        String existing = feignDiscovered.get(fieldMock.getType());
        if (existing != null && !existing.isEmpty()) {
            return null;
        }
        return JustMockDefinition.decapitalize(fieldMock.getType().getSimpleName());
    }

    /**
     * 字段 {@code @JustMock} 解析到的 Bean 若已由 {@code @ThreadScopedMock} 或 Feign auto-mock 覆盖，
     * 返回那个 Bean 名；否则返回 null，调用方按原逻辑替换定义。
     */
    String bindTarget(JustMockDefinition fieldMock,
                      String resolved,
                      Map<String, JustMockDefinition> threadScopedByName,
                      Map<Class<?>, String> feignDiscovered,
                      Set<Class<?>> feignBlocked) {
        if (resolved != null && threadScopedByName.containsKey(resolved)) {
            return resolved;
        }
        if (feignDiscovered == null || feignDiscovered.isEmpty()
                || feignBlocked.contains(fieldMock.getType())
                || !feignDiscovered.containsKey(fieldMock.getType())) {
            return null;
        }
        String existing = feignDiscovered.get(fieldMock.getType());
        String feignName = existing == null || existing.isEmpty()
                ? JustMockDefinition.decapitalize(fieldMock.getType().getSimpleName())
                : existing;
        return feignName.equals(resolved) ? feignName : null;
    }

    Map<Class<?>, String> discoverFeignTypes(ConfigurableListableBeanFactory beanFactory,
                                             BeanDefinitionRegistry registry) {
        if (!registry.containsBeanDefinition(JustTestProjectFeignAutoMock.BEAN_NAME)
                && !beanFactory.containsSingleton(JustTestProjectFeignAutoMock.BEAN_NAME)) {
            return Collections.emptyMap();
        }
        JustTestProjectFeignAutoMock settings = beanFactory.getBean(
                JustTestProjectFeignAutoMock.BEAN_NAME, JustTestProjectFeignAutoMock.class);
        return settings.discover(beanFactory);
    }

    Set<JustMockDefinition> feignDefinitions(Map<Class<?>, String> discovered,
                                             Set<Class<?>> blocked) {
        Set<JustMockDefinition> definitions = new LinkedHashSet<JustMockDefinition>();
        if (discovered == null || discovered.isEmpty()) {
            return definitions;
        }
        for (Map.Entry<Class<?>, String> entry : discovered.entrySet()) {
            Class<?> type = entry.getKey();
            if (blocked.contains(type)) {
                log.debug("[JustMock] Skipping auto @FeignClient {} because an explicit mock or @JustSpy already exists",
                        type.getName());
                continue;
            }
            definitions.add(JustMockDefinition.forAutoMock(
                    type, entry.getValue(), JustTestProjectFeignAutoMock.SOURCE));
            log.info("[JustMock] Auto-mocking @FeignClient {}", type.getName());
        }
        return definitions;
    }

    private String registerThreadScopedMock(ConfigurableListableBeanFactory beanFactory,
                                            BeanDefinitionRegistry registry,
                                            JustMockDefinition definition,
                                            Map<String, JustMockDefinition> spiesByBeanName) {
        String targetBeanName = names.resolveBeanName(beanFactory, registry, definition);
        JustMockDefinition spy = spiesByBeanName.get(targetBeanName);
        if (spy != null) {
            throw JustMockBeanOrigins.sameBeanSpyMockConflict(targetBeanName, spy, definition);
        }
        Class<?> mockType = names.resolveMockType(beanFactory, registry, targetBeanName, definition);
        FinalMethodMockGuard.check(mockType);
        BeanDefinition originalDefinition = null;
        BeanDefinition originalScopedTarget = null;
        String[] aliases = null;
        if (registry.containsBeanDefinition(targetBeanName)) {
            originalDefinition = registry.getBeanDefinition(targetBeanName);
            aliases = beanFactory.getAliases(targetBeanName).clone();
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
        restoreBeanAliases(beanFactory, registry, targetBeanName, aliases);

        log.info("[JustMock] Registered thread-scoped mock for {} as '{}'",
                mockType.getSimpleName(), targetBeanName);
        return targetBeanName;
    }

    /**
     * 替换或隐藏后只按规范名重新注册。{@code removeBeanDefinition} 可能同时丢掉别名，
     * 所以先快照，新定义落定后再 {@code registerAlias}。名字若已是 Bean 定义、
     * 单例，或指向其他 Bean 的别名，则跳过并警告，不覆盖已有注册。
     */
    void restoreBeanAliases(ConfigurableListableBeanFactory beanFactory,
                            BeanDefinitionRegistry registry,
                            String beanName,
                            String[] aliases) {
        if (aliases == null || aliases.length == 0) {
            return;
        }
        for (String alias : aliases) {
            if (alias == null || alias.length() == 0 || alias.equals(beanName)) {
                continue;
            }
            if (aliasConflicts(beanFactory, registry, beanName, alias)) {
                log.warn("[JustMock] Skipping alias '{}' for bean '{}': it conflicts with an existing "
                        + "bean name or alias. The existing registration is left unchanged.",
                        alias, beanName);
                // 同名 Bean 定义已经存在时，仍指向本 Bean 的旧别名会盖住它。摘掉这条别名，不改那份定义。
                if (registry.containsBeanDefinition(alias) && aliasesBean(registry, beanName, alias)) {
                    registry.removeAlias(alias);
                }
                continue;
            }
            registry.registerAlias(beanName, alias);
        }
    }

    private static boolean aliasesBean(BeanDefinitionRegistry registry, String beanName, String alias) {
        for (String existing : registry.getAliases(beanName)) {
            if (alias.equals(existing)) {
                return true;
            }
        }
        return false;
    }

    private static boolean aliasConflicts(ConfigurableListableBeanFactory beanFactory,
                                          BeanDefinitionRegistry registry,
                                          String beanName,
                                          String alias) {
        if (registry.containsBeanDefinition(alias) || beanFactory.containsSingleton(alias)) {
            return true;
        }
        return registry.isAlias(alias) && !aliasesBean(registry, beanName, alias);
    }

    String registerThreadScopedSpy(ConfigurableListableBeanFactory beanFactory,
                                   BeanDefinitionRegistry registry,
                                   JustMockDefinition definition,
                                   String publicName) {
        if (!registry.containsBeanDefinition(publicName)) {
            throw new IllegalStateException("[JustSpy] No bean definition named '" + publicName
                    + "' to wrap for " + definition.describe()
                    + ". @JustSpy does not create a bean.");
        }
        Class<?> mockType = names.resolveMockType(beanFactory, registry, publicName, definition);
        FinalMethodMockGuard.check(mockType);
        BeanDefinition original = registry.getBeanDefinition(publicName);
        String scopedTargetName = ScopedProxyUtils.getTargetBeanName(publicName);
        BeanDefinition originalScopedTarget = registry.containsBeanDefinition(scopedTargetName)
                ? registry.getBeanDefinition(scopedTargetName) : null;
        String hiddenName = JustMockPostProcessor.spyTargetBeanName(publicName);

        AbstractBeanDefinition mockDef = createSpyBeanDefinition(mockType, beanFactory, hiddenName);
        copyAutowireMetadata(mockDef, original, originalScopedTarget);

        if (originalScopedTarget != null) {
            registry.removeBeanDefinition(scopedTargetName);
            String hiddenScopedName = ScopedProxyUtils.getTargetBeanName(hiddenName);
            retargetScopedProxy(original, hiddenScopedName);
            hideRealTarget(originalScopedTarget);
            registry.registerBeanDefinition(hiddenScopedName, originalScopedTarget);
        }

        BeanDefinitionHolder holder = new BeanDefinitionHolder(mockDef, publicName);
        BeanDefinitionHolder proxy = ScopedProxyUtils.createScopedProxy(holder, registry, true);
        if (proxy.getBeanDefinition() instanceof AbstractBeanDefinition) {
            copyAutowireMetadata((AbstractBeanDefinition) proxy.getBeanDefinition(),
                    original, originalScopedTarget);
        }

        registry.removeBeanDefinition(publicName);
        hideRealTarget(original);
        registry.registerBeanDefinition(hiddenName, original);
        registry.registerBeanDefinition(publicName, proxy.getBeanDefinition());
        log.info("[JustSpy] Registered thread-scoped spy for {} as '{}' delegating to '{}'",
                mockType.getSimpleName(), publicName, hiddenName);
        return publicName;
    }

    void retargetScopedProxy(BeanDefinition definition, String hiddenScopedTargetName) {
        if (definition.getPropertyValues().contains("targetBeanName")) {
            definition.getPropertyValues().add("targetBeanName", hiddenScopedTargetName);
        }
    }

    void hideRealTarget(BeanDefinition definition) {
        definition.setAutowireCandidate(false);
        definition.setPrimary(false);
        definition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
    }

    private <T> AbstractBeanDefinition createSpyBeanDefinition(final Class<T> mockType,
                                                               final ConfigurableListableBeanFactory beanFactory,
                                                               final String realBeanName) {
        return BeanDefinitionBuilder
                .genericBeanDefinition(mockType, () -> createDelegatingMock(mockType, beanFactory, realBeanName))
                .setScope("thread")
                .getBeanDefinition();
    }

    private <T> T createDelegatingMock(Class<T> mockType,
                                       ConfigurableListableBeanFactory beanFactory,
                                       String realBeanName) {
        Object realBean = beanFactory.getBean(realBeanName);
        if (!mockType.isInstance(realBean)) {
            throw new IllegalStateException("[JustSpy] Bean '" + realBeanName + "' is not a "
                    + mockType.getName() + "; cannot delegate unstubbed calls to it.");
        }
        return Mockito.mock(mockType, threadScopedMockSettings()
                .defaultAnswer(AdditionalAnswers.delegatesTo(realBean)));
    }

    void markMockSource(BeanDefinitionRegistry registry, String beanName,
                        JustMockDefinition definition) {
        String source = definition.sourceLabel();
        markSourceAttribute(registry, beanName, source);
        markSourceAttribute(registry, ScopedProxyUtils.getTargetBeanName(beanName), source);
    }

    void markSourceAttribute(BeanDefinitionRegistry registry, String beanName, String source) {
        if (registry.containsBeanDefinition(beanName)) {
            registry.getBeanDefinition(beanName)
                    .setAttribute(JustMockBeanOrigins.SOURCE_ATTRIBUTE, source);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> AbstractBeanDefinition createMockBeanDefinition(Class<T> mockType) {
        return BeanDefinitionBuilder
                .genericBeanDefinition(mockType, () -> Mockito.mock(mockType, threadScopedMockSettings()))
                .setScope("thread")
                .getBeanDefinition();
    }

    /**
     * {@code AbstractAdvisingBeanPostProcessor} 与 {@code AbstractAutoProxyCreator}
     * 会跳过 {@link AopInfrastructureBean}。线程作用域 mock 带上这个标记后，
     * {@code @Repository} 的异常转换、事务自动代理等不会再包住 mock 本身。
     * 未 stub 的 {@code @JustSpy} 调用因此只经过真实 Bean 上的那一层 advisor。
     * 隐藏真实 Bean 仍是普通实例，继续按原定义被自动代理。
     */
    static MockSettings threadScopedMockSettings() {
        return Mockito.withSettings().extraInterfaces(AopInfrastructureBean.class);
    }

    void copyAutowireMetadata(AbstractBeanDefinition target,
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

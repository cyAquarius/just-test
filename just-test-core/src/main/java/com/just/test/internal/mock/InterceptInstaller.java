package com.just.test.internal.mock;

import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 合并并安装 {@code @JustTestProject} 的真实 Bean 拦截。
 *
 * <p>同包实现，不是公开 API。</p>
 */
class InterceptInstaller {

    // 日志类别保持 JustMockPostProcessor，拆分不改变现有日志名。
    private static final Logger log = LoggerFactory.getLogger(JustMockPostProcessor.class);

    private final JustMockInterceptPlan interceptPlan;
    private final BeanNameResolver names;
    private final MockRegistrar registrar;

    InterceptInstaller(JustMockInterceptPlan interceptPlan,
               BeanNameResolver names,
               MockRegistrar registrar) {
        this.interceptPlan = interceptPlan;
        this.names = names;
        this.registrar = registrar;
    }

    Map<Class<?>, String> installIntercepts(ConfigurableListableBeanFactory beanFactory,
                                            BeanDefinitionRegistry registry,
                                            Set<JustMockDefinition> threadScoped,
                                            Map<String, JustMockDefinition> spiesByBeanName,
                                            Map<Class<?>, String> feignDiscovered,
                                            Map<JustMockDefinition, String> bindings,
                                            Set<String> registeredBeanNames,
                                            Set<String> interceptBeanNames,
                                            Set<Class<?>> feignBlocked) {
        Map<Class<?>, String> installed = new LinkedHashMap<Class<?>, String>();
        if (!interceptPlan.isProject()) {
            return installed;
        }
        // 先按原始 Bean 定义解析全部声明类型，再按 Bean 名合并注册。
        // 实现类先于接口安装时，不能把已经包过的公开代理再当成真实 Bean 包第二层。
        List<InterceptResolution> resolutions = new ArrayList<InterceptResolution>();
        Set<Class<?>> seen = new LinkedHashSet<Class<?>>();
        for (Class<?> type : interceptPlan.explicitTypes()) {
            if (!seen.add(type)) {
                continue;
            }
            InterceptResolution resolution = resolveIntercept(
                    beanFactory, registry, type, true, threadScoped, spiesByBeanName, feignDiscovered);
            if (resolution != null) {
                resolutions.add(resolution);
            }
        }
        for (Class<?> type : interceptPlan.scannedTypes()) {
            if (interceptPlan.isExplicit(type) || !seen.add(type)) {
                continue;
            }
            InterceptResolution resolution = resolveIntercept(
                    beanFactory, registry, type, false, threadScoped, spiesByBeanName, feignDiscovered);
            if (resolution != null) {
                resolutions.add(resolution);
            }
        }
        for (InterceptResolution resolution : resolutions) {
            if (resolution.ambiguous()) {
                throw ambiguousIntercept(resolution, resolutions);
            }
        }
        Map<String, List<InterceptResolution>> byBean =
                new LinkedHashMap<String, List<InterceptResolution>>();
        for (InterceptResolution resolution : resolutions) {
            List<InterceptResolution> group = byBean.get(resolution.beanName);
            if (group == null) {
                group = new ArrayList<InterceptResolution>();
                byBean.put(resolution.beanName, group);
            }
            group.add(resolution);
        }
        for (Map.Entry<String, List<InterceptResolution>> entry : byBean.entrySet()) {
            registerMergedIntercept(beanFactory, registry, entry.getKey(), entry.getValue(),
                    bindings, registeredBeanNames, interceptBeanNames, feignBlocked, installed);
        }
        return installed;
    }

    private InterceptResolution resolveIntercept(ConfigurableListableBeanFactory beanFactory,
                                                 BeanDefinitionRegistry registry,
                                                 Class<?> type,
                                                 boolean explicit,
                                                 Set<JustMockDefinition> threadScoped,
                                                 Map<String, JustMockDefinition> spiesByBeanName,
                                                 Map<Class<?>, String> feignDiscovered) {
        if (feignDiscovered != null && feignDiscovered.containsKey(type)) {
            if (explicit) {
                throw alreadyThreadScoped(type);
            }
            log.debug("[JustMock] {} is a Feign auto-mock; @JustMock binds and is not intercepted",
                    type.getName());
            return null;
        }
        Set<String> threadScopedNames = threadScopedByNames(threadScoped);
        LinkedHashSet<String> candidates = names.collectLogicalCandidates(beanFactory, type, false);
        boolean onlyThreadScoped = !candidates.isEmpty() && threadScopedNames.containsAll(candidates);
        candidates.removeAll(threadScopedNames);
        if (candidates.isEmpty() && onlyThreadScoped) {
            if (explicit) {
                throw alreadyThreadScoped(type);
            }
            log.debug("[JustMock] {} is already a thread-scoped mock; @JustMock binds and is not intercepted",
                    type.getName());
            return null;
        }
        if (candidates.size() > 1) {
            return InterceptResolution.ambiguous(type, candidates);
        }
        if (candidates.isEmpty()) {
            if (explicit) {
                throw new IllegalStateException("[JustMock] @JustTestProject(interceptMocks) lists "
                        + type.getName() + " on " + interceptPlan.startupClass().getName()
                        + " but no real bean of that type exists. "
                        + "Intercept wraps the existing unique bean and does not create one.");
            }
            log.debug("[JustMock] No real bean of {} to intercept; a later @JustMock of this type fails fast",
                    type.getName());
            return null;
        }
        String publicName = candidates.iterator().next();
        if (spiesByBeanName.containsKey(publicName)) {
            log.debug("[JustMock] {} is a @JustSpy target in this context and is not intercepted", publicName);
            return null;
        }
        Class<?> mockType = names.resolveMockType(beanFactory, registry, publicName,
                JustMockDefinition.forBeanName(type, publicName, JustMockInterceptActivation.SOURCE));
        return InterceptResolution.unique(type, publicName, mockType);
    }

    private void registerMergedIntercept(ConfigurableListableBeanFactory beanFactory,
                                         BeanDefinitionRegistry registry,
                                         String publicName,
                                         List<InterceptResolution> group,
                                         Map<JustMockDefinition, String> bindings,
                                         Set<String> registeredBeanNames,
                                         Set<String> interceptBeanNames,
                                         Set<Class<?>> feignBlocked,
                                         Map<Class<?>, String> installed) {
        Class<?> mockType = mockTypeForGroup(beanFactory, publicName, group);
        FinalMethodMockGuard.check(mockType);
        assertMockable(mockType);
        registerThreadScopedIntercept(beanFactory, registry, mockType, publicName);
        registeredBeanNames.add(publicName);
        interceptBeanNames.add(publicName);
        LinkedHashSet<Class<?>> boundTypes = new LinkedHashSet<Class<?>>();
        boundTypes.add(mockType);
        for (InterceptResolution resolution : group) {
            boundTypes.add(resolution.type);
            rememberIntercept(installed, resolution.type, publicName);
            feignBlocked.add(resolution.type);
        }
        rememberIntercept(installed, mockType, publicName);
        feignBlocked.add(mockType);
        for (Class<?> boundType : boundTypes) {
            bindings.put(JustMockDefinition.forBeanName(
                    boundType, publicName, JustMockInterceptActivation.SOURCE), publicName);
        }
        registrar.markSourceAttribute(registry, publicName, JustMockInterceptActivation.SOURCE);
        registrar.markSourceAttribute(registry, ScopedProxyUtils.getTargetBeanName(publicName),
                JustMockInterceptActivation.SOURCE);
        log.info("[JustMock] Registered thread intercept for {} as '{}'", mockType.getName(), publicName);
        if (group.size() > 1) {
            log.info("[JustMock] Merged declaration types {} onto thread intercept '{}'",
                    declarationTypes(group), publicName);
        }
    }

    /**
     * 同一 Bean 只选一个 mock 类型。单个声明保持原先的 {@code resolveMockType} 结果。
     * 多个声明必须都能接受这个 mock（字段类型是 mock 类型的超类型或相同类型）。
     */
    private Class<?> mockTypeForGroup(ConfigurableListableBeanFactory beanFactory,
                                      String beanName,
                                      List<InterceptResolution> group) {
        if (group.size() == 1) {
            return group.get(0).mockType;
        }
        Class<?> beanType = names.safeBeanType(beanFactory, beanName);
        Class<?> mockType = beanType;
        if (mockType == null) {
            mockType = group.get(0).mockType;
            for (InterceptResolution resolution : group) {
                if (mockType.isAssignableFrom(resolution.mockType)) {
                    mockType = resolution.mockType;
                }
            }
        }
        for (InterceptResolution resolution : group) {
            if (mockType == null || !resolution.type.isAssignableFrom(mockType)) {
                throw unsafeMerge(beanName, mockType, group);
            }
        }
        if (beanType != null && !mockType.isAssignableFrom(beanType)) {
            throw unsafeMerge(beanName, mockType, group);
        }
        return mockType;
    }

    private IllegalStateException unsafeMerge(String beanName,
                                              Class<?> mockType,
                                              List<InterceptResolution> group) {
        String mockTypeName = mockType == null ? "(unknown)" : mockType.getName();
        return new IllegalStateException("[JustMock] Cannot merge thread intercept onto bean '"
                + beanName + "'. Incompatible mock type " + mockTypeName
                + ". Declaration types: " + declarationTypes(group)
                + ". Declared by: " + declarers(group)
                + ". JustTest will not register a second intercept.");
    }

    private IllegalStateException ambiguousIntercept(InterceptResolution ambiguous,
                                                     List<InterceptResolution> resolutions) {
        StringBuilder conflicts = new StringBuilder();
        for (InterceptResolution resolution : resolutions) {
            if (resolution.ambiguous() || resolution.beanName == null
                    || !ambiguous.candidates.contains(resolution.beanName)) {
                continue;
            }
            if (conflicts.length() > 0) {
                conflicts.append("; ");
            }
            conflicts.append(resolution.type.getName())
                    .append(" -> '")
                    .append(resolution.beanName)
                    .append("' declared by ")
                    .append(interceptPlan.declaredBy(resolution.type));
        }
        StringBuilder message = new StringBuilder();
        message.append("[JustMock] Cannot merge thread intercept for declaration type ")
                .append(ambiguous.type.getName())
                .append(" declared by ")
                .append(interceptPlan.declaredBy(ambiguous.type))
                .append(". Multiple beans found: ")
                .append(ambiguous.candidates)
                .append('.');
        if (conflicts.length() > 0) {
            message.append(" Conflicting declaration types: ").append(conflicts).append('.');
        }
        message.append(" JustTest will not choose between them or register a second intercept.");
        return new IllegalStateException(message.toString());
    }

    private static String declarationTypes(List<InterceptResolution> group) {
        StringBuilder builder = new StringBuilder();
        for (InterceptResolution resolution : group) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(resolution.type.getName());
        }
        return builder.toString();
    }

    private String declarers(List<InterceptResolution> group) {
        StringBuilder builder = new StringBuilder();
        for (InterceptResolution resolution : group) {
            if (builder.length() > 0) {
                builder.append("; ");
            }
            builder.append(resolution.type.getName())
                    .append(" by ")
                    .append(interceptPlan.declaredBy(resolution.type));
        }
        return builder.toString();
    }

    private static final class InterceptResolution {
        private final Class<?> type;
        private final String beanName;
        private final Class<?> mockType;
        private final LinkedHashSet<String> candidates;

        private InterceptResolution(Class<?> type,
                                    String beanName,
                                    Class<?> mockType,
                                    LinkedHashSet<String> candidates) {
            this.type = type;
            this.beanName = beanName;
            this.mockType = mockType;
            this.candidates = candidates;
        }

        private static InterceptResolution unique(Class<?> type, String beanName, Class<?> mockType) {
            return new InterceptResolution(type, beanName, mockType, null);
        }

        private static InterceptResolution ambiguous(Class<?> type, LinkedHashSet<String> candidates) {
            return new InterceptResolution(type, null, null, candidates);
        }

        private boolean ambiguous() {
            return candidates != null;
        }
    }

    private static void rememberIntercept(Map<Class<?>, String> installed, Class<?> type, String beanName) {
        String previous = installed.put(type, beanName);
        if (previous != null && !previous.equals(beanName)) {
            throw new IllegalStateException("[JustMock] Intercept type " + type.getName()
                    + " resolved to both '" + previous + "' and '" + beanName
                    + "'. JustTest does not expose a second bean of the same type.");
        }
    }

    static String installedIntercept(Class<?> type, Map<Class<?>, String> installed) {
        String exact = installed.get(type);
        if (exact != null) {
            return exact;
        }
        String matched = null;
        for (Map.Entry<Class<?>, String> entry : installed.entrySet()) {
            if (!type.isAssignableFrom(entry.getKey()) && !entry.getKey().isAssignableFrom(type)) {
                continue;
            }
            if (matched != null && !matched.equals(entry.getValue())) {
                throw new IllegalStateException("[JustMock] Multiple thread intercepts match "
                        + type.getName() + ". Candidates include '" + matched + "' and '"
                        + entry.getValue() + "'. JustTest does not choose between them "
                        + "or auto-exclude auto-configurations.");
            }
            matched = entry.getValue();
        }
        return matched;
    }

    private static Set<String> threadScopedByNames(Set<JustMockDefinition> threadScoped) {
        Set<String> names = new LinkedHashSet<String>();
        for (JustMockDefinition definition : threadScoped) {
            if (definition.getExplicitBeanName() != null && !definition.getExplicitBeanName().isEmpty()) {
                names.add(definition.getExplicitBeanName());
            }
        }
        return names;
    }

    private static IllegalStateException alreadyThreadScoped(Class<?> type) {
        return new IllegalStateException("[JustMock] " + type.getName()
                + " is already a thread-scoped mock (@ThreadScopedMock or Feign auto-mock). "
                + "Do not list it in @JustTestProject(interceptMocks); @JustMock binds to that mock "
                + "and does not wrap it again.");
    }

    private static void assertMockable(Class<?> type) {
        try {
            Mockito.mock(type, MockRegistrar.threadScopedMockSettings());
        } catch (RuntimeException ex) {
            throw new IllegalStateException("[JustMock] Cannot intercept " + type.getName()
                    + " at context start. Mockito cannot proxy this type "
                    + "(final class, or the current mock-maker cannot mock it). "
                    + ex.getMessage(), ex);
        }
    }

    private void registerThreadScopedIntercept(ConfigurableListableBeanFactory beanFactory,
                                               BeanDefinitionRegistry registry,
                                               Class<?> mockType,
                                               String publicName) {
        if (!registry.containsBeanDefinition(publicName)) {
            throw new IllegalStateException("[JustMock] No bean definition named '" + publicName
                    + "' to intercept for " + mockType.getName()
                    + ". Intercept wraps an existing bean and does not create one.");
        }
        BeanDefinition original = registry.getBeanDefinition(publicName);
        String scopedTargetName = ScopedProxyUtils.getTargetBeanName(publicName);
        BeanDefinition originalScopedTarget = registry.containsBeanDefinition(scopedTargetName)
                ? registry.getBeanDefinition(scopedTargetName) : null;
        String hiddenName = JustMockPostProcessor.INTERCEPT_TARGET_PREFIX + publicName;

        AbstractBeanDefinition mockDef = createInterceptBeanDefinition(
                mockType, beanFactory, hiddenName, publicName);
        registrar.copyAutowireMetadata(mockDef, original, originalScopedTarget);

        if (originalScopedTarget != null) {
            registry.removeBeanDefinition(scopedTargetName);
            String hiddenScopedName = ScopedProxyUtils.getTargetBeanName(hiddenName);
            registrar.retargetScopedProxy(original, hiddenScopedName);
            registrar.hideRealTarget(originalScopedTarget);
            registry.registerBeanDefinition(hiddenScopedName, originalScopedTarget);
        }

        BeanDefinitionHolder holder = new BeanDefinitionHolder(mockDef, publicName);
        BeanDefinitionHolder proxy = ScopedProxyUtils.createScopedProxy(holder, registry, true);
        if (proxy.getBeanDefinition() instanceof AbstractBeanDefinition) {
            registrar.copyAutowireMetadata((AbstractBeanDefinition) proxy.getBeanDefinition(),
                    original, originalScopedTarget);
        }

        String[] aliases = beanFactory.getAliases(publicName).clone();
        registry.removeBeanDefinition(publicName);
        registrar.hideRealTarget(original);
        registry.registerBeanDefinition(hiddenName, original);
        registry.registerBeanDefinition(publicName, proxy.getBeanDefinition());
        registrar.restoreBeanAliases(beanFactory, registry, publicName, aliases);
    }

    private <T> AbstractBeanDefinition createInterceptBeanDefinition(final Class<T> mockType,
                                                                     final ConfigurableListableBeanFactory beanFactory,
                                                                     final String hiddenName,
                                                                     final String publicName) {
        return BeanDefinitionBuilder
                .genericBeanDefinition(mockType, () -> createInterceptMock(
                        mockType, beanFactory, hiddenName, publicName))
                .setScope("thread")
                .getBeanDefinition();
    }

    private <T> T createInterceptMock(Class<T> mockType,
                                      ConfigurableListableBeanFactory beanFactory,
                                      String hiddenName,
                                      String publicName) {
        Object realBean = beanFactory.getBean(hiddenName);
        if (!mockType.isInstance(realBean)) {
            throw new IllegalStateException("[JustMock] Bean '" + hiddenName + "' is not a "
                    + mockType.getName() + "; cannot delegate unstubbed calls to it.");
        }
        return Mockito.mock(mockType, MockRegistrar.threadScopedMockSettings().defaultAnswer(
                new JustMockInterceptActivation.AnswerSwitch(
                        publicName, AdditionalAnswers.delegatesTo(realBean))));
    }
}

package com.just.test.internal.project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * {@code @JustTestProject(autoMockFeignClients = true)} 时发现 {@code @FeignClient}
 * 接口，供 {@code JustMockPostProcessor} 注册线程作用域 mock。
 *
 * <p>只认 {@code org.springframework.cloud.openfeign.FeignClient} 注解存在，
 * 不按 {@code *Client} 名字推断，也不引入 OpenFeign 编译依赖。</p>
 *
 * <p>不对消费方提供兼容承诺。</p>
 */
public final class JustTestProjectFeignAutoMock {

    public static final String BEAN_NAME = "justTestProjectFeignAutoMock";
    public static final String ATTRIBUTE = "autoMockFeignClients";
    public static final String EXCLUDES_ATTRIBUTE = "autoMockFeignClientExcludes";
    public static final String FEIGN_CLIENT_ANNOTATION = "org.springframework.cloud.openfeign.FeignClient";
    public static final String FEIGN_CLIENT_FACTORY_BEAN =
            "org.springframework.cloud.openfeign.FeignClientFactoryBean";
    public static final String SOURCE = "auto @FeignClient";

    private static final Logger log = LoggerFactory.getLogger(JustTestProjectFeignAutoMock.class);
    private static final String SCOPED_TARGET_PREFIX = "scopedTarget.";

    private final String[] basePackages;
    private final Set<String> excludeClassNames;

    JustTestProjectFeignAutoMock(String[] basePackages, Class<?>[] excludes) {
        this.basePackages = basePackages == null ? new String[0] : basePackages;
        this.excludeClassNames = classNames(excludes);
    }

    static void register(BeanDefinitionRegistry registry, String[] basePackages, Class<?>[] excludes) {
        if (registry.containsBeanDefinition(BEAN_NAME)) {
            return;
        }
        final String[] packages = basePackages == null ? new String[0] : basePackages;
        final Class<?>[] excludeTypes = excludes;
        AbstractBeanDefinition definition = BeanDefinitionBuilder
                .genericBeanDefinition(JustTestProjectFeignAutoMock.class,
                        () -> new JustTestProjectFeignAutoMock(packages, excludeTypes))
                .getBeanDefinition();
        definition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        definition.setAutowireCandidate(false);
        registry.registerBeanDefinition(BEAN_NAME, definition);
    }

    String[] getBasePackages() {
        return basePackages;
    }

    Set<String> getExcludeClassNames() {
        return excludeClassNames;
    }

    /**
     * 发现应自动 mock 的 {@code @FeignClient} 接口。
     *
     * <p>值为已有 Bean 名；空字符串表示 classpath 扫描到、尚未注册的类型，
     * 由 mock 注册器按默认名创建。</p>
     */
    public Map<Class<?>, String> discover(ConfigurableListableBeanFactory beanFactory) {
        ClassLoader classLoader = beanFactory.getBeanClassLoader();
        Class<? extends Annotation> feignClient = loadAnnotation(FEIGN_CLIENT_ANNOTATION, classLoader);
        if (feignClient == null) {
            log.debug("[JustTest] autoMockFeignClients is on, but {} is not on the classpath",
                    FEIGN_CLIENT_ANNOTATION);
            return Collections.emptyMap();
        }
        Map<Class<?>, String> discovered = new LinkedHashMap<Class<?>, String>();
        addRegisteredFeignClients(beanFactory, classLoader, feignClient, discovered);
        addScannedFeignClients(classLoader, feignClient, discovered);
        return Collections.unmodifiableMap(discovered);
    }

    private void addRegisteredFeignClients(ConfigurableListableBeanFactory beanFactory,
                                           ClassLoader classLoader,
                                           Class<? extends Annotation> feignClient,
                                           Map<Class<?>, String> discovered) {
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            if (beanName.startsWith(SCOPED_TARGET_PREFIX)) {
                continue;
            }
            Class<?> type = resolveRegisteredFeignType(beanFactory, beanName, classLoader, feignClient);
            if (type == null || !accept(type)) {
                continue;
            }
            if (!discovered.containsKey(type) || !StringUtils.hasText(discovered.get(type))) {
                discovered.put(type, beanName);
            }
        }
    }

    private void addScannedFeignClients(ClassLoader classLoader,
                                        Class<? extends Annotation> feignClient,
                                        Map<Class<?>, String> discovered) {
        ClassPathScanningCandidateComponentProvider scanner = feignInterfaceScanner(classLoader, feignClient);
        for (String basePackage : basePackages) {
            if (!StringUtils.hasText(basePackage)) {
                continue;
            }
            Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage.trim());
            for (BeanDefinition candidate : candidates) {
                Class<?> type = loadClass(candidate.getBeanClassName(), classLoader);
                if (type == null || !accept(type) || discovered.containsKey(type)) {
                    continue;
                }
                discovered.put(type, "");
            }
        }
    }

    private boolean accept(Class<?> type) {
        return type.isInterface()
                && !type.isAnnotation()
                && !excludeClassNames.contains(type.getName());
    }

    static ClassPathScanningCandidateComponentProvider feignInterfaceScanner(
            ClassLoader classLoader, Class<? extends Annotation> feignClient) {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                        AnnotationMetadata metadata = beanDefinition.getMetadata();
                        return metadata.isIndependent() && metadata.isInterface() && !metadata.isAnnotation();
                    }
                };
        scanner.setResourceLoader(new PathMatchingResourcePatternResolver(classLoader));
        scanner.addIncludeFilter(new AnnotationTypeFilter(feignClient, false, true));
        return scanner;
    }

    static Class<?> resolveRegisteredFeignType(ConfigurableListableBeanFactory beanFactory,
                                               String beanName,
                                               ClassLoader classLoader,
                                               Class<? extends Annotation> feignClient) {
        BeanDefinition definition = beanFactory.getBeanDefinition(beanName);
        Class<?> factoryType = readFeignFactoryBeanType(definition, classLoader);
        if (factoryType != null && isFeignClient(factoryType, feignClient)) {
            return factoryType;
        }
        Class<?> beanType = beanFactory.getType(beanName, false);
        if (beanType != null && isFeignClient(beanType, feignClient)) {
            return beanType;
        }
        return null;
    }

    static Class<?> readFeignFactoryBeanType(BeanDefinition definition, ClassLoader classLoader) {
        if (!isFeignClientFactoryBean(definition)) {
            return null;
        }
        Object type = definition.getPropertyValues().get("type");
        if (type instanceof Class) {
            return (Class<?>) type;
        }
        if (type instanceof String && StringUtils.hasText((String) type)) {
            return loadClass((String) type, classLoader);
        }
        return null;
    }

    static boolean isFeignClientFactoryBean(BeanDefinition definition) {
        String className = definition.getBeanClassName();
        if (FEIGN_CLIENT_FACTORY_BEAN.equals(className)) {
            return true;
        }
        if (definition instanceof AbstractBeanDefinition
                && ((AbstractBeanDefinition) definition).hasBeanClass()) {
            return FEIGN_CLIENT_FACTORY_BEAN.equals(
                    ((AbstractBeanDefinition) definition).getBeanClass().getName());
        }
        return false;
    }

    static boolean isFeignClient(Class<?> type, Class<? extends Annotation> feignClient) {
        return type != null && type.isInterface() && !type.isAnnotation()
                && type.getAnnotation(feignClient) != null;
    }

    static Set<String> classNames(Class<?>[] types) {
        if (types == null || types.length == 0) {
            return Collections.emptySet();
        }
        Set<String> names = new LinkedHashSet<String>();
        for (Class<?> type : types) {
            if (type != null) {
                names.add(type.getName());
            }
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    static Class<? extends Annotation> loadAnnotation(String className, ClassLoader classLoader) {
        Class<?> type = loadClass(className, classLoader);
        if (type == null || !type.isAnnotation()) {
            return null;
        }
        return (Class<? extends Annotation>) type;
    }

    static Class<?> loadClass(String className, ClassLoader classLoader) {
        if (!StringUtils.hasText(className) || !ClassUtils.isPresent(className, classLoader)) {
            return null;
        }
        try {
            return ClassUtils.forName(className, classLoader);
        } catch (ClassNotFoundException ex) {
            return null;
        } catch (LinkageError ex) {
            return null;
        }
    }
}

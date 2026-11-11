package com.just.test.internal.mock;

import com.just.test.annotation.ThreadScopedMock;
import com.just.test.internal.project.JustTestProjectFeignAutoMock;
import com.just.test.internal.project.JustTestProjectTypeExcludeFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Profiles;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 从测试类能确定会加载的配置里，找出框架已知的线程级 mock 来源。
 *
 * <p>只认 {@code @ThreadScopedMock} 的 {@code @Bean}，以及
 * {@code @JustTestProject(autoMockFeignClients = true)} 扫到的 {@code @FeignClient}。
 * 不按类名推断。带额外 {@code @Conditional}（{@code @Profile} 除外）或同类型还有其他
 * 用户 Bean 时，不把 {@code @JustMock} 从缓存 key 里拿掉。</p>
 */
final class JustMockKnownSources {

    private static final Logger log = LoggerFactory.getLogger(JustMockKnownSources.class);
    private static final String SPRING_BOOT_CONFIGURATION =
            "org.springframework.boot.SpringBootConfiguration";
    private static final String TEST_CONFIGURATION =
            "org.springframework.boot.test.context.TestConfiguration";
    private static final String JUST_TEST_PROJECT = "com.just.test.annotation.JustTestProject";

    private static final Map<String, JustMockKnownSources> CACHE =
            new ConcurrentHashMap<String, JustMockKnownSources>();

    private final List<BeanCandidate> beans;
    private final Set<Class<?>> feignTypes;

    private JustMockKnownSources(List<BeanCandidate> beans, Set<Class<?>> feignTypes) {
        this.beans = beans;
        this.feignTypes = feignTypes;
    }

    static JustMockKnownSources forTest(Class<?> testClass, List<ContextConfigurationAttributes> attributes) {
        LinkedHashSet<Class<?>> roots = configurationRoots(testClass, attributes);
        String[] profiles = activeProfiles(testClass);
        String cacheKey = cacheKey(roots, profiles);
        JustMockKnownSources cached = CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        JustMockKnownSources created = discover(testClass, roots, profiles);
        JustMockKnownSources raced = CACHE.putIfAbsent(cacheKey, created);
        return raced == null ? created : raced;
    }

    /**
     * 这条 {@code @JustMock} 是否一定只绑定到已有线程级 mock，从而不会改变 Bean 定义。
     * 显式 {@code name} 只在能对上 {@code @ThreadScopedMock} 的 Bean 名时才算确定；
     * Feign 工厂 Bean 的运行时名字不一定等于简单类名，因此带 name 的 Feign 声明留在缓存 key 里。
     */
    boolean isConfidentBind(JustMockDefinition definition) {
        if (definition == null || definition.isSpy() || definition.hasQualifierAnnotations()) {
            return false;
        }
        List<BeanCandidate> compatible = compatible(definition.getType());
        for (BeanCandidate candidate : compatible) {
            if (candidate.conditional) {
                return false;
            }
        }
        List<BeanCandidate> threadScoped = new ArrayList<BeanCandidate>();
        List<BeanCandidate> others = new ArrayList<BeanCandidate>();
        for (BeanCandidate candidate : compatible) {
            if (candidate.threadScoped) {
                threadScoped.add(candidate);
            } else {
                others.add(candidate);
            }
        }
        if (threadScoped.size() == 1 && others.isEmpty() && !feignTypes.contains(definition.getType())) {
            // Feign 工厂 Bean 不一定来自 @Bean 方法。同类型再叠一层 @ThreadScopedMock 时，
            // 候选可能不止一个，留给启动期诊断，不从缓存 key 里拿掉。
            return nameMatches(definition, threadScoped.get(0));
        }
        if (threadScoped.isEmpty() && others.isEmpty() && feignTypes.contains(definition.getType())) {
            return definition.getExplicitBeanName().isEmpty();
        }
        return false;
    }

    private List<BeanCandidate> compatible(Class<?> fieldType) {
        List<BeanCandidate> compatible = new ArrayList<BeanCandidate>();
        for (BeanCandidate candidate : beans) {
            if (candidate.type != Object.class && fieldType.isAssignableFrom(candidate.type)) {
                compatible.add(candidate);
            }
        }
        return compatible;
    }

    private static boolean nameMatches(JustMockDefinition definition, BeanCandidate candidate) {
        String explicit = definition.getExplicitBeanName();
        if (explicit == null || explicit.isEmpty()) {
            return true;
        }
        if (explicit.equals(candidate.beanName)) {
            return true;
        }
        for (String alias : candidate.aliases) {
            if (explicit.equals(alias)) {
                return true;
            }
        }
        return false;
    }

    private static JustMockKnownSources discover(Class<?> testClass,
                                                  LinkedHashSet<Class<?>> roots,
                                                  String[] profiles) {
        StandardEnvironment environment = new StandardEnvironment();
        if (profiles != null && profiles.length > 0) {
            environment.setActiveProfiles(profiles);
        }
        Collector collector = new Collector(testClass.getClassLoader(), environment);
        for (Class<?> root : roots) {
            collector.consumeRoot(root);
        }
        collector.finishStructure();
        return new JustMockKnownSources(collector.beans, collector.feignTypes);
    }

    private static LinkedHashSet<Class<?>> configurationRoots(Class<?> testClass,
                                                               List<ContextConfigurationAttributes> attributes) {
        LinkedHashSet<Class<?>> classes = new LinkedHashSet<Class<?>>();
        if (attributes != null) {
            for (ContextConfigurationAttributes attribute : attributes) {
                Class<?>[] configured = attribute.getClasses();
                if (configured != null) {
                    classes.addAll(Arrays.asList(configured));
                }
            }
        }
        if (classes.isEmpty()) {
            collectDeclaredConfiguration(testClass, classes);
        }
        if (containsNonTestComponent(classes)) {
            return classes;
        }
        Class<?> boot = findBootConfiguration(testClass);
        if (boot != null) {
            classes.add(boot);
        }
        return classes;
    }

    private static void collectDeclaredConfiguration(Class<?> testClass, Set<Class<?>> classes) {
        for (Class<?> current = testClass; current != null && current != Object.class;
             current = current.getSuperclass()) {
            ContextConfiguration declared = current.getDeclaredAnnotation(ContextConfiguration.class);
            if (declared == null) {
                continue;
            }
            classes.addAll(Arrays.asList(declared.classes()));
            if (!declared.inheritLocations()) {
                return;
            }
        }
    }

    private static boolean containsNonTestComponent(Collection<Class<?>> classes) {
        for (Class<?> candidate : classes) {
            if (!isTestConfiguration(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTestConfiguration(Class<?> type) {
        Class<? extends Annotation> annotation = loadAnnotation(TEST_CONFIGURATION, type.getClassLoader());
        return annotation != null && AnnotatedElementUtils.hasAnnotation(type, annotation);
    }

    static Class<?> findBootConfiguration(Class<?> testClass) {
        String packageName = ClassUtils.getPackageName(testClass);
        PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver(testClass.getClassLoader());
        CachingMetadataReaderFactory factory = new CachingMetadataReaderFactory(resolver);
        while (packageName != null && !packageName.isEmpty()) {
            String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
                    + ClassUtils.convertClassNameToResourcePath(packageName) + "/*.class";
            Resource[] resources;
            try {
                resources = resolver.getResources(pattern);
            } catch (IOException ex) {
                log.debug("[JustMock] Could not scan {} for @SpringBootConfiguration", packageName, ex);
                return null;
            }
            List<String> found = new ArrayList<String>();
            for (Resource resource : resources) {
                if (!resource.isReadable()) {
                    continue;
                }
                try {
                    MetadataReader reader = factory.getMetadataReader(resource);
                    ClassMetadata metadata = reader.getClassMetadata();
                    if (!metadata.isIndependent()) {
                        continue;
                    }
                    if (reader.getAnnotationMetadata().isAnnotated(SPRING_BOOT_CONFIGURATION)) {
                        found.add(metadata.getClassName());
                    }
                } catch (IOException ex) {
                    log.debug("[JustMock] Skipping unreadable class while searching for @SpringBootConfiguration", ex);
                }
            }
            if (found.size() == 1) {
                return loadClass(found.get(0), testClass.getClassLoader());
            }
            if (found.size() > 1) {
                log.debug("[JustMock] Multiple @SpringBootConfiguration types in {}: {}", packageName, found);
                return null;
            }
            int dot = packageName.lastIndexOf('.');
            packageName = dot < 0 ? "" : packageName.substring(0, dot);
        }
        return null;
    }

    /**
     * {@code ActiveProfilesUtils} 是 Spring 的包内类型。这里只合并测试类及其元注解上的
     * {@code @ActiveProfiles}，用来判断 {@code @Profile} 是否会挡住 {@code @ThreadScopedMock}。
     */
    private static String[] activeProfiles(Class<?> testClass) {
        LinkedHashSet<String> profiles = new LinkedHashSet<String>();
        for (Class<?> current = testClass; current != null && current != Object.class;
             current = current.getSuperclass()) {
            boolean inherit = true;
            ActiveProfiles direct = current.getDeclaredAnnotation(ActiveProfiles.class);
            if (direct != null) {
                addProfiles(profiles, direct);
                inherit = direct.inheritProfiles();
            }
            for (Annotation annotation : current.getDeclaredAnnotations()) {
                ActiveProfiles meta = annotation.annotationType().getAnnotation(ActiveProfiles.class);
                if (meta != null) {
                    addProfiles(profiles, meta);
                    inherit = inherit && meta.inheritProfiles();
                }
            }
            if (!inherit) {
                break;
            }
        }
        return profiles.toArray(new String[profiles.size()]);
    }

    private static void addProfiles(Set<String> profiles, ActiveProfiles active) {
        addProfileValues(profiles, active.value());
        addProfileValues(profiles, active.profiles());
    }

    private static void addProfileValues(Set<String> profiles, String[] values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                profiles.add(value.trim());
            }
        }
    }

    private static String cacheKey(Set<Class<?>> roots, String[] profiles) {
        List<String> rootNames = new ArrayList<String>();
        for (Class<?> root : roots) {
            rootNames.add(root.getName());
        }
        Collections.sort(rootNames);
        List<String> profileNames = new ArrayList<String>();
        if (profiles != null) {
            profileNames.addAll(Arrays.asList(profiles));
        }
        Collections.sort(profileNames);
        return rootNames.toString() + "#" + profileNames;
    }

    private static Class<?> loadClass(String className, ClassLoader classLoader) {
        if (className == null || !ClassUtils.isPresent(className, classLoader)) {
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

    @SuppressWarnings("unchecked")
    private static Class<? extends Annotation> loadAnnotation(String className, ClassLoader classLoader) {
        Class<?> type = loadClass(className, classLoader);
        if (type == null || !type.isAnnotation()) {
            return null;
        }
        return (Class<? extends Annotation>) type;
    }

    private static final class BeanCandidate {
        private final Class<?> type;
        private final String beanName;
        private final List<String> aliases;
        private final boolean threadScoped;
        private final boolean conditional;

        private BeanCandidate(Class<?> type, String beanName, List<String> aliases,
                               boolean threadScoped, boolean conditional) {
            this.type = type;
            this.beanName = beanName;
            this.aliases = aliases;
            this.threadScoped = threadScoped;
            this.conditional = conditional;
        }
    }

    private static final class Collector {
        private final ClassLoader classLoader;
        private final StandardEnvironment environment;
        private final Set<String> visited = new LinkedHashSet<String>();
        private final Set<String> seenBeanNames = new LinkedHashSet<String>();
        private final List<BeanCandidate> beans = new ArrayList<BeanCandidate>();
        private final Set<Class<?>> feignTypes = new LinkedHashSet<Class<?>>();
        private final Set<String> scanPackages = new LinkedHashSet<String>();
        private final List<Class<?>> projectRoots = new ArrayList<Class<?>>();
        private boolean autoMockFeign;
        private final Set<Class<?>> feignExcludes = new LinkedHashSet<Class<?>>();
        private final Set<Class<?>> componentExcludes = new LinkedHashSet<Class<?>>();

        private Collector(ClassLoader classLoader, StandardEnvironment environment) {
            this.classLoader = classLoader;
            this.environment = environment;
        }

        private void consumeRoot(Class<?> root) {
            if (root == null) {
                return;
            }
            AnnotationAttributes project = annotationAttributes(root, JUST_TEST_PROJECT);
            if (project != null) {
                projectRoots.add(root);
                addPackages(project.getStringArray("basePackages"));
                addPackages(project.getStringArray("value"));
                if (project.getBoolean("autoMockFeignClients")) {
                    autoMockFeign = true;
                }
                addClasses(feignExcludes, project.getClassArray("autoMockFeignClientExcludes"));
                addClasses(componentExcludes, project.getClassArray("excludeClasses"));
            } else {
                addPackages(componentScanPackages(root));
            }
            visitClass(root);
        }

        private void finishStructure() {
            if (!scanPackages.isEmpty()) {
                scanComponents();
            }
            if (autoMockFeign) {
                scanFeignClients();
            }
        }

        private void visitClass(Class<?> type) {
            if (type == null || type == Object.class || !visited.add(type.getName())) {
                return;
            }
            if (!profileActive(type)) {
                return;
            }
            readBeanMethods(type);
            for (Class<?> nested : type.getDeclaredClasses()) {
                if (Modifier.isStatic(nested.getModifiers())
                        && nested.isAnnotationPresent(Configuration.class)) {
                    visitClass(nested);
                }
            }
            Import imported = type.getDeclaredAnnotation(Import.class);
            if (imported != null) {
                for (Class<?> item : imported.value()) {
                    if (item.isAnnotationPresent(Configuration.class) || declaresBeanMethod(item)) {
                        visitClass(item);
                    }
                }
            }
        }

        private void readBeanMethods(Class<?> type) {
            boolean classConditional = hasNonProfileCondition(type);
            for (Class<?> current = type; current != null && current != Object.class
                    && !current.getName().startsWith("java."); current = current.getSuperclass()) {
                if (!profileActive(current)) {
                    continue;
                }
                boolean superclassConditional = current == type
                        ? classConditional
                        : hasNonProfileCondition(current);
                for (Method method : current.getDeclaredMethods()) {
                    if (method.isBridge() || method.isSynthetic() || method.getAnnotation(Bean.class) == null) {
                        continue;
                    }
                    if (!profileActive(method)) {
                        continue;
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType == null || returnType == void.class || returnType == Object.class
                            || returnType.isPrimitive()) {
                        continue;
                    }
                    Bean bean = method.getAnnotation(Bean.class);
                    String[] declared = beanNames(bean);
                    String beanName = declared.length == 0 ? method.getName() : declared[0];
                    if (!seenBeanNames.add(beanName)) {
                        continue;
                    }
                    List<String> aliases = new ArrayList<String>();
                    for (int i = 1; i < declared.length; i++) {
                        aliases.add(declared[i]);
                    }
                    boolean threadScoped = method.getAnnotation(ThreadScopedMock.class) != null;
                    boolean conditional = superclassConditional || hasNonProfileCondition(method);
                    beans.add(new BeanCandidate(returnType, beanName, aliases, threadScoped, conditional));
                }
            }
        }

        private void scanComponents() {
            ClassPathScanningCandidateComponentProvider scanner =
                    new ClassPathScanningCandidateComponentProvider(true);
            scanner.setResourceLoader(new PathMatchingResourcePatternResolver(classLoader));
            if (!projectRoots.isEmpty()) {
                Class<?>[] excludes = componentExcludes.toArray(new Class<?>[componentExcludes.size()]);
                scanner.addExcludeFilter(new JustTestProjectTypeExcludeFilter(
                        projectRoots.get(0).getName(), excludes, classLoader));
            }
            for (String basePackage : scanPackages) {
                Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);
                for (BeanDefinition candidate : candidates) {
                    Class<?> type = loadClass(candidate.getBeanClassName(), classLoader);
                    visitClass(type);
                }
            }
        }

        private void scanFeignClients() {
            Class<? extends Annotation> feignClient = loadAnnotation(
                    JustTestProjectFeignAutoMock.FEIGN_CLIENT_ANNOTATION, classLoader);
            if (feignClient == null) {
                return;
            }
            ClassPathScanningCandidateComponentProvider scanner = feignScanner(feignClient);
            for (String basePackage : scanPackages) {
                Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);
                for (BeanDefinition candidate : candidates) {
                    Class<?> type = loadClass(candidate.getBeanClassName(), classLoader);
                    if (type == null || !type.isInterface() || type.isAnnotation()
                            || feignExcludes.contains(type)) {
                        continue;
                    }
                    if (type.getAnnotation(feignClient) != null) {
                        feignTypes.add(type);
                    }
                }
            }
        }

        private ClassPathScanningCandidateComponentProvider feignScanner(
                final Class<? extends Annotation> feignClient) {
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

        private boolean profileActive(AnnotatedElement element) {
            Profile profile = AnnotatedElementUtils.findMergedAnnotation(element, Profile.class);
            if (profile == null || profile.value().length == 0) {
                return true;
            }
            return environment.acceptsProfiles(Profiles.of(profile.value()));
        }

        private static boolean hasNonProfileCondition(AnnotatedElement element) {
            for (Annotation annotation : element.getAnnotations()) {
                Class<? extends Annotation> type = annotation.annotationType();
                if (type == Profile.class) {
                    continue;
                }
                if (type == Conditional.class || AnnotatedElementUtils.hasAnnotation(type, Conditional.class)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean declaresBeanMethod(Class<?> type) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getAnnotation(Bean.class) != null) {
                    return true;
                }
            }
            return false;
        }

        private static String[] beanNames(Bean bean) {
            String[] names = bean.name();
            if (names.length == 0) {
                names = bean.value();
            }
            List<String> present = new ArrayList<String>();
            for (String name : names) {
                if (name != null && !name.isEmpty()) {
                    present.add(name);
                }
            }
            return present.toArray(new String[present.size()]);
        }

        private static String[] componentScanPackages(Class<?> root) {
            AnnotationAttributes scan = annotationAttributes(root, ComponentScan.class.getName());
            if (scan == null) {
                return new String[0];
            }
            String[] packages = scan.getStringArray("basePackages");
            if (packages.length == 0) {
                packages = scan.getStringArray("value");
            }
            if (packages.length > 0) {
                return packages;
            }
            Class<?>[] types = scan.getClassArray("basePackageClasses");
            if (types.length > 0) {
                String[] fromTypes = new String[types.length];
                for (int i = 0; i < types.length; i++) {
                    fromTypes[i] = ClassUtils.getPackageName(types[i]);
                }
                return fromTypes;
            }
            return new String[] {ClassUtils.getPackageName(root)};
        }

        private static AnnotationAttributes annotationAttributes(Class<?> type, String annotationName) {
            try {
                return AnnotatedElementUtils.getMergedAnnotationAttributes(type, annotationName);
            } catch (RuntimeException ex) {
                log.debug("[JustMock] Could not read {} on {}", annotationName, type.getName(), ex);
                return null;
            }
        }

        private void addPackages(String[] packages) {
            if (packages == null) {
                return;
            }
            for (String basePackage : packages) {
                if (basePackage != null && !basePackage.trim().isEmpty()) {
                    scanPackages.add(basePackage.trim());
                }
            }
        }

        private static void addClasses(Set<Class<?>> target, Class<?>[] types) {
            if (types == null) {
                return;
            }
            for (Class<?> type : types) {
                if (type != null) {
                    target.add(type);
                }
            }
        }
    }

}

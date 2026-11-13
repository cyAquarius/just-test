package com.just.test.internal.mock;

import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustTestMarker;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.junit.jupiter.api.Disabled;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 同一个 {@code @JustTestProject} 启动类下，第一次 Context 启动前就要确定的拦截类型。
 *
 * <p>显式名单来自 {@code @JustTestProject(interceptMocks)}。另外只并入「启动类身份相同」
 * 的 {@code @JustTest} 上声明的 {@code @JustMock} 字段类型：比较的是启动类本身，
 * 不复制 Spring Test 的 context 等价判断，也不再扫描 Bean 定义。</p>
 */
final class JustMockInterceptPlan {

    private static final String JUST_TEST_PROJECT = "com.just.test.annotation.JustTestProject";
    private static final String INTERCEPT_MOCKS = "interceptMocks";

    private static final JustMockInterceptPlan NONE =
            new JustMockInterceptPlan(null, Collections.<Class<?>>emptySet(),
                    Collections.<Class<?>>emptySet(), Collections.<Class<?>, Set<String>>emptyMap());

    private static final ConcurrentHashMap<String, Union> CACHE =
            new ConcurrentHashMap<String, Union>();

    private final Class<?> startupClass;
    private final Set<Class<?>> explicitTypes;
    private final Set<Class<?>> scannedTypes;
    private final Map<Class<?>, Set<String>> declaredBy;

    private JustMockInterceptPlan(Class<?> startupClass,
                                  Set<Class<?>> explicitTypes,
                                  Set<Class<?>> scannedTypes,
                                  Map<Class<?>, Set<String>> declaredBy) {
        this.startupClass = startupClass;
        this.explicitTypes = explicitTypes;
        this.scannedTypes = scannedTypes;
        this.declaredBy = declaredBy;
    }

    static JustMockInterceptPlan none() {
        return NONE;
    }

    /**
     * 测试用：指定启动类与类型全集，不走 classpath 扫描。
     */
    static JustMockInterceptPlan project(Class<?> startupClass,
                                         Collection<Class<?>> explicitTypes,
                                         Collection<Class<?>> scannedTypes) {
        return project(startupClass, explicitTypes, scannedTypes,
                Collections.<Class<?>, Collection<Class<?>>>emptyMap());
    }

    /**
     * 测试用。{@code declaredBy} 把声明类型映到声明它的测试类，供无法合并时的诊断使用。
     */
    static JustMockInterceptPlan project(Class<?> startupClass,
                                         Collection<Class<?>> explicitTypes,
                                         Collection<Class<?>> scannedTypes,
                                         Map<Class<?>, ? extends Collection<Class<?>>> declaredBy) {
        Map<Class<?>, LinkedHashSet<String>> names = new LinkedHashMap<Class<?>, LinkedHashSet<String>>();
        if (declaredBy != null) {
            for (Map.Entry<Class<?>, ? extends Collection<Class<?>>> entry : declaredBy.entrySet()) {
                rememberAll(names, entry.getKey(), entry.getValue());
            }
        }
        if (startupClass != null && explicitTypes != null) {
            for (Class<?> type : explicitTypes) {
                if (type != null && !names.containsKey(type)) {
                    rememberDeclarer(names, type, startupClass.getName());
                }
            }
        }
        return new JustMockInterceptPlan(
                startupClass,
                copy(explicitTypes),
                copy(scannedTypes),
                freeze(names));
    }

    static JustMockInterceptPlan forTest(Class<?> testClass, List<ContextConfigurationAttributes> attributes) {
        if (testClass == null) {
            return NONE;
        }
        Class<?> startup = startupClass(testClass, attributes);
        if (startup == null) {
            return NONE;
        }
        String cacheKey = startup.getName() + '#' + System.identityHashCode(startup.getClassLoader());
        Union cached = CACHE.get(cacheKey);
        if (cached == null) {
            Union created = discover(startup);
            Union raced = CACHE.putIfAbsent(cacheKey, created);
            cached = raced == null ? created : raced;
        }
        return new JustMockInterceptPlan(
                startup, cached.explicitTypes, cached.scannedTypes, cached.declaredBy);
    }

    boolean isProject() {
        return startupClass != null;
    }

    Class<?> startupClass() {
        return startupClass;
    }

    boolean isExplicit(Class<?> type) {
        return explicitTypes.contains(type);
    }

    Set<Class<?>> explicitTypes() {
        return explicitTypes;
    }

    Set<Class<?>> scannedTypes() {
        return scannedTypes;
    }

    /**
     * 声明了 {@code type} 的测试类，或把该类型写进 {@code interceptMocks} 的启动类。
     * 没有记录时返回固定占位，避免诊断里出现空列表。
     */
    String declaredBy(Class<?> type) {
        Set<String> names = type == null ? null : declaredBy.get(type);
        if (names == null || names.isEmpty()) {
            return "(no test class recorded)";
        }
        StringBuilder builder = new StringBuilder();
        for (String name : names) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(name);
        }
        return builder.toString();
    }

    IllegalStateException notInstalled(JustMockDefinition definition) {
        String startup = startupClass == null ? "(no @JustTestProject)" : startupClass.getName();
        return new IllegalStateException("[JustMock] " + definition.describe()
                + " was not installed as a thread intercept. "
                + "JustTest will not replace that bean definition or start another Spring Test context. "
                + "Add " + definition.getType().getName()
                + " to @JustTestProject(interceptMocks = ...) on " + startup
                + ", or check that this @JustTest class is included in the startup-class scan "
                + "(it must use that same startup class and live in its package or a subpackage).");
    }

    private static Union discover(Class<?> startup) {
        Map<Class<?>, LinkedHashSet<String>> declaredBy = new LinkedHashMap<Class<?>, LinkedHashSet<String>>();
        Set<Class<?>> explicit = explicitTypes(startup, declaredBy);
        Set<Class<?>> scanned = scanJustMockTypes(startup, declaredBy);
        return new Union(explicit, scanned, freeze(declaredBy));
    }

    private static Set<Class<?>> explicitTypes(Class<?> startup,
                                               Map<Class<?>, LinkedHashSet<String>> declaredBy) {
        AnnotationAttributes attributes = AnnotatedElementUtils.getMergedAnnotationAttributes(
                startup, JUST_TEST_PROJECT);
        if (attributes == null || !attributes.containsKey(INTERCEPT_MOCKS)) {
            return Collections.emptySet();
        }
        Class<?>[] declared = attributes.getClassArray(INTERCEPT_MOCKS);
        if (declared == null || declared.length == 0) {
            return Collections.emptySet();
        }
        for (Class<?> type : declared) {
            rememberDeclarer(declaredBy, type, startup.getName());
        }
        return copy(Arrays.asList(declared));
    }

    private static Set<Class<?>> scanJustMockTypes(Class<?> startup,
                                                   Map<Class<?>, LinkedHashSet<String>> declaredBy) {
        String basePackage = ClassUtils.getPackageName(startup);
        if (basePackage == null || basePackage.isEmpty()) {
            return Collections.emptySet();
        }
        ClassLoader loader = startup.getClassLoader();
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                        return beanDefinition.getMetadata().isConcrete()
                                && beanDefinition.getMetadata().isIndependent();
                    }
                };
        scanner.setResourceLoader(new PathMatchingResourcePatternResolver(loader));
        scanner.addIncludeFilter(new AnnotationTypeFilter(JustTestMarker.class));
        Set<Class<?>> types = new LinkedHashSet<Class<?>>();
        for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
            Class<?> testClass = load(candidate.getBeanClassName(), loader);
            if (testClass == null || testClass == startup) {
                continue;
            }
            if (startupClass(testClass, Collections.<ContextConfigurationAttributes>emptyList()) != startup) {
                continue;
            }
            // 类级 @Disabled（含组合注解、接口上的 @Disabled）的测试不会执行。
            // 它们的真实 Bean @JustMock 不得进入共享拦截并集，否则会 fail-fast 整个 Context，
            // 或改掉无关 Bean 的生命周期。@Disabled 没有 @Inherited，父类上的不算到子类。
            // 方法级 @Disabled 不跳过：类仍会运行，字段声明仍然有效。
            if (isDisabled(testClass)) {
                continue;
            }
            collectJustMockTypes(testClass, types, declaredBy);
        }
        return Collections.unmodifiableSet(types);
    }

    /**
     * 与 JUnit 5.9 {@code DisabledCondition} 一样：类上直接或经元注解出现的 {@code @Disabled}，
     * 以及接口上的 {@code @Disabled}。不沿父类查找，因为 {@code @Disabled} 不是 {@code @Inherited}。
     */
    private static boolean isDisabled(Class<?> type) {
        if (type == null || type == Object.class) {
            return false;
        }
        if (AnnotatedElementUtils.hasAnnotation(type, Disabled.class)) {
            return true;
        }
        for (Class<?> iface : type.getInterfaces()) {
            if (isDisabled(iface)) {
                return true;
            }
        }
        return false;
    }

    private static void collectJustMockTypes(Class<?> testClass,
                                             Set<Class<?>> types,
                                             Map<Class<?>, LinkedHashSet<String>> declaredBy) {
        for (Class<?> current = testClass; current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isSynthetic() || field.getAnnotation(JustMock.class) == null) {
                    continue;
                }
                Class<?> fieldType = field.getType();
                if (fieldType.isPrimitive() || fieldType.isArray()) {
                    continue;
                }
                types.add(fieldType);
                rememberDeclarer(declaredBy, fieldType, testClass.getName());
            }
        }
    }

    private static void rememberAll(Map<Class<?>, LinkedHashSet<String>> declaredBy,
                                    Class<?> type,
                                    Collection<Class<?>> testClasses) {
        if (testClasses == null) {
            return;
        }
        for (Class<?> testClass : testClasses) {
            if (testClass != null) {
                rememberDeclarer(declaredBy, type, testClass.getName());
            }
        }
    }

    private static void rememberDeclarer(Map<Class<?>, LinkedHashSet<String>> declaredBy,
                                         Class<?> type,
                                         String declarer) {
        if (type == null || declarer == null || declarer.isEmpty()) {
            return;
        }
        LinkedHashSet<String> names = declaredBy.get(type);
        if (names == null) {
            names = new LinkedHashSet<String>();
            declaredBy.put(type, names);
        }
        names.add(declarer);
    }

    private static Map<Class<?>, Set<String>> freeze(Map<Class<?>, LinkedHashSet<String>> declaredBy) {
        if (declaredBy.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Class<?>, Set<String>> frozen = new LinkedHashMap<Class<?>, Set<String>>();
        for (Map.Entry<Class<?>, LinkedHashSet<String>> entry : declaredBy.entrySet()) {
            frozen.put(entry.getKey(), Collections.unmodifiableSet(
                    new LinkedHashSet<String>(entry.getValue())));
        }
        return Collections.unmodifiableMap(frozen);
    }

    /**
     * 测试实际使用的 {@code @JustTestProject} 启动类。显式 {@code classes} 优先；
     * 没有显式 classes 时才沿包向上找 {@code @SpringBootConfiguration}。
     * 找到的类必须带 {@code @JustTestProject}，否则本测试不走拦截。
     */
    private static Class<?> startupClass(Class<?> testClass, List<ContextConfigurationAttributes> attributes) {
        List<Class<?>> explicit = explicitClasses(testClass, attributes);
        for (Class<?> candidate : explicit) {
            if (isProject(candidate)) {
                return candidate;
            }
        }
        if (!explicit.isEmpty()) {
            return null;
        }
        Class<?> discovered = JustMockKnownSources.findBootConfiguration(testClass);
        return isProject(discovered) ? discovered : null;
    }

    private static boolean isProject(Class<?> type) {
        return type != null && AnnotatedElementUtils.isAnnotated(type, JUST_TEST_PROJECT);
    }

    private static List<Class<?>> explicitClasses(Class<?> testClass,
                                                  List<ContextConfigurationAttributes> attributes) {
        LinkedHashSet<Class<?>> classes = new LinkedHashSet<Class<?>>();
        if (attributes != null) {
            for (ContextConfigurationAttributes attribute : attributes) {
                if (attribute == null || attribute.getClasses() == null) {
                    continue;
                }
                classes.addAll(Arrays.asList(attribute.getClasses()));
            }
        }
        if (!classes.isEmpty()) {
            return new ArrayList<Class<?>>(classes);
        }
        for (Class<?> current = testClass; current != null && current != Object.class;
             current = current.getSuperclass()) {
            ContextConfiguration declared = current.getDeclaredAnnotation(ContextConfiguration.class);
            if (declared == null) {
                continue;
            }
            classes.addAll(Arrays.asList(declared.classes()));
            if (!declared.inheritLocations()) {
                break;
            }
        }
        return new ArrayList<Class<?>>(classes);
    }

    private static Class<?> load(String className, ClassLoader loader) {
        if (className == null || !ClassUtils.isPresent(className, loader)) {
            return null;
        }
        try {
            return Class.forName(className, false, loader);
        } catch (ClassNotFoundException ex) {
            return null;
        } catch (LinkageError ex) {
            return null;
        }
    }

    private static Set<Class<?>> copy(Collection<Class<?>> types) {
        if (types == null || types.isEmpty()) {
            return Collections.emptySet();
        }
        LinkedHashSet<Class<?>> copy = new LinkedHashSet<Class<?>>();
        for (Class<?> type : types) {
            if (type != null) {
                copy.add(type);
            }
        }
        return Collections.unmodifiableSet(copy);
    }

    private static final class Union {
        private final Set<Class<?>> explicitTypes;
        private final Set<Class<?>> scannedTypes;
        private final Map<Class<?>, Set<String>> declaredBy;

        private Union(Set<Class<?>> explicitTypes,
                      Set<Class<?>> scannedTypes,
                      Map<Class<?>, Set<String>> declaredBy) {
            this.explicitTypes = explicitTypes;
            this.scannedTypes = scannedTypes;
            this.declaredBy = declaredBy;
        }
    }
}

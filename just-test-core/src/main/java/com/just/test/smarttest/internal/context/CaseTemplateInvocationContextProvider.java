package com.just.test.smarttest.internal.context;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTestMarker;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.internal.lifecycle.SmartTestExtension;
import com.just.test.smarttest.internal.loader.DataSetLoader;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 为每个 YAML case 创建独立的 JUnit test-template invocation。
 *
 * <p>不对消费方提供兼容承诺。由 {@code @CaseSource} 的 {@code @ExtendWith} 实例化，
 * 因此必须保持 public。</p>
 */
public class CaseTemplateInvocationContextProvider implements TestTemplateInvocationContextProvider {

    private static final Logger log = LoggerFactory.getLogger(CaseTemplateInvocationContextProvider.class);

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        boolean caseSource = context.getTestMethod()
                .map(method -> method.isAnnotationPresent(CaseSource.class))
                .orElse(false);
        if (caseSource && !AnnotatedElementUtils.hasAnnotation(
                context.getRequiredTestClass(), SmartTestMarker.class)) {
            throw new ExtensionConfigurationException(String.format(
                    "[SmartTest] %s uses @CaseSource without @SmartTest. "
                            + "Add @SmartTest to the test class or use a standard JUnit test annotation.",
                    context.getRequiredTestClass().getName()));
        }
        return caseSource;
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
            ExtensionContext context) {
        return discoverCases(context).stream().map(CaseInvocationContext::new);
    }

    List<CaseContext> discoverCases(ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();
        Package testPackage = testClass.getPackage();
        String packageName = testPackage == null ? "" : testPackage.getName();
        String packagePath = packageName.replace('.', '/');
        rejectMultipleConcreteSmartTestClasses(packageName, packagePath);

        CaseSource annotation = context.getRequiredTestMethod().getAnnotation(CaseSource.class);
        String configuredRoot = annotation.value().trim();
        String caseRoot = configuredRoot.isEmpty()
                ? packagePath
                : joinPath(packagePath, configuredRoot);

        Set<String> caseNames = findCaseNames(caseRoot);
        if (caseNames.isEmpty()) {
            throw new ExtensionConfigurationException(String.format(
                    "[SmartTest] No YAML case directories found: testClass=%s, caseRoot=%s. "
                            + "Expected case directories as siblings under the test class package "
                            + "(classpath %s/{case}/*.yaml|yml), or under an explicit @CaseSource root. "
                            + "Default discovery uses the test class package as the case root and does not "
                            + "probe {package}/{SimpleClassName}/.",
                    testClass.getName(),
                    caseRoot,
                    packagePath.isEmpty() ? "<package>" : packagePath));
        }

        return caseNames.stream()
                .map(caseName -> toCaseContext(caseName, caseRoot))
                .collect(Collectors.toList());
    }

    private void rejectMultipleConcreteSmartTestClasses(String packageName, String packagePath) {
        List<String> concreteClasses = findConcreteSmartTestClassNames(packageName, packagePath);
        if (concreteClasses.size() <= 1) {
            return;
        }
        throw new ExtensionConfigurationException(String.format(
                "[SmartTest] Package %s has multiple concrete @SmartTest classes: %s. "
                        + "Expected one concrete @SmartTest class per package, with case directories "
                        + "as siblings under the test class package.",
                packageName.isEmpty() ? "<default>" : packageName,
                String.join(", ", concreteClasses)));
    }

    private List<String> findConcreteSmartTestClassNames(String packageName, String packagePath) {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resolver);
            String pattern = packagePath.isEmpty()
                    ? "classpath*:*.class"
                    : "classpath*:" + packagePath + "/*.class";
            Resource[] resources = resolver.getResources(pattern);
            Set<String> names = new TreeSet<String>();
            for (Resource resource : resources) {
                if (!resource.isReadable()) {
                    continue;
                }
                MetadataReader reader = metadataReaderFactory.getMetadataReader(resource);
                ClassMetadata metadata = reader.getClassMetadata();
                if (!metadata.isConcrete()) {
                    continue;
                }
                if (!hasSmartTestMarker(reader)) {
                    continue;
                }
                names.add(metadata.getClassName());
            }
            return new ArrayList<String>(names);
        } catch (Exception e) {
            throw new ExtensionConfigurationException(
                    "[SmartTest] Failed to scan @SmartTest classes in package "
                            + (packageName.isEmpty() ? "<default>" : packageName),
                    e);
        }
    }

    private boolean hasSmartTestMarker(MetadataReader reader) {
        return reader.getAnnotationMetadata().hasAnnotation(SmartTestMarker.class.getName())
                || reader.getAnnotationMetadata().hasMetaAnnotation(SmartTestMarker.class.getName());
    }

    private Set<String> findCaseNames(String caseRoot) {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            List<Resource> resources = new ArrayList<>();
            Collections.addAll(resources, resolver.getResources("classpath*:" + caseRoot + "/*/*.yaml"));
            Collections.addAll(resources, resolver.getResources("classpath*:" + caseRoot + "/*/*.yml"));

            Set<String> caseNames = new TreeSet<>();
            String marker = caseRoot.isEmpty() ? "" : caseRoot + "/";
            for (Resource resource : resources) {
                String location = resource.getURL().toExternalForm();
                int markerIndex = location.lastIndexOf(marker);
                if (markerIndex < 0) {
                    continue;
                }
                String relative = location.substring(markerIndex + marker.length());
                int separator = relative.indexOf('/');
                if (separator > 0 && relative.indexOf('/', separator + 1) < 0) {
                    caseNames.add(relative.substring(0, separator));
                }
            }
            return caseNames;
        } catch (Exception e) {
            throw new ExtensionConfigurationException(
                    "[SmartTest] Failed to discover YAML cases under " + caseRoot, e);
        }
    }

    private CaseContext toCaseContext(String caseName, String caseRoot) {
        String casePath = joinPath(caseRoot, caseName);
        Map<String, Object> data = DataSetLoader.parseYamlByPath(casePath, "request.yaml");
        Map<String, Object> params = data != null ? data : Collections.emptyMap();
        log.debug("[SmartTest] Discovered case: {} (path: {})", caseName, casePath);
        return new CaseContext(caseName, casePath, params);
    }

    private String joinPath(String parent, String child) {
        return parent.isEmpty() ? child : parent + "/" + child;
    }

    private static final class CaseInvocationContext implements TestTemplateInvocationContext {

        private final CaseContext caseContext;

        private CaseInvocationContext(CaseContext caseContext) {
            this.caseContext = caseContext;
        }

        @Override
        public String getDisplayName(int invocationIndex) {
            return caseContext.getCaseName();
        }

        @Override
        public List<Extension> getAdditionalExtensions() {
            return Collections.<Extension>singletonList(new SmartTestExtension(caseContext));
        }
    }
}

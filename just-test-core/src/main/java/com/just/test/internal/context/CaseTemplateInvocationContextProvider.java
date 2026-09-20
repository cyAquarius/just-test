package com.just.test.internal.context;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTestMarker;
import com.just.test.context.CaseContext;
import com.just.test.internal.lifecycle.JustTestExtension;
import com.just.test.internal.loader.DataSetLoader;
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
        if (caseSource && !hasDirectJustTestMarker(context.getRequiredTestClass())) {
            throw new ExtensionConfigurationException(String.format(
                    "[JustTest] %s uses @CaseSource without @JustTest. "
                            + "Add @JustTest to the concrete test class; abstract Support / base "
                            + "classes must not carry @JustTest.",
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
        rejectInvalidJustTestClasses(packageName, packagePath);

        CaseSource annotation = context.getRequiredTestMethod().getAnnotation(CaseSource.class);
        String configuredRoot = annotation.value().trim();
        String caseRoot = configuredRoot.isEmpty()
                ? packagePath
                : joinPath(packagePath, configuredRoot);

        Set<String> caseNames = findCaseNames(caseRoot);
        if (caseNames.isEmpty()) {
            throw new ExtensionConfigurationException(String.format(
                    "[JustTest] No YAML case directories found: testClass=%s, caseRoot=%s. "
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

    private void rejectInvalidJustTestClasses(String packageName, String packagePath) {
        PackageJustTestScan scan = scanPackageJustTestClasses(packageName, packagePath);
        if (!scan.abstractClassNames.isEmpty()) {
            throw new ExtensionConfigurationException(String.format(
                    "[JustTest] Package %s has abstract @JustTest class(es): %s. "
                            + "Abstract classes must not carry @JustTest; put it on the concrete "
                            + "method-package test class.",
                    displayPackageName(packageName),
                    String.join(", ", scan.abstractClassNames)));
        }
        if (scan.concreteClassNames.size() <= 1) {
            return;
        }
        throw new ExtensionConfigurationException(String.format(
                "[JustTest] Package %s has multiple concrete @JustTest classes: %s. "
                        + "Expected one concrete @JustTest class per package, with case directories "
                        + "as siblings under the test class package.",
                displayPackageName(packageName),
                String.join(", ", scan.concreteClassNames)));
    }

    private PackageJustTestScan scanPackageJustTestClasses(String packageName, String packagePath) {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resolver);
            String pattern = packagePath.isEmpty()
                    ? "classpath*:*.class"
                    : "classpath*:" + packagePath + "/*.class";
            Resource[] resources = resolver.getResources(pattern);
            Set<String> abstractNames = new TreeSet<String>();
            Set<String> concreteNames = new TreeSet<String>();
            for (Resource resource : resources) {
                if (!resource.isReadable()) {
                    continue;
                }
                MetadataReader reader = metadataReaderFactory.getMetadataReader(resource);
                ClassMetadata metadata = reader.getClassMetadata();
                if (!hasJustTestMarker(reader)) {
                    continue;
                }
                if (metadata.isInterface()) {
                    continue;
                }
                if (metadata.isAbstract()) {
                    abstractNames.add(metadata.getClassName());
                    continue;
                }
                concreteNames.add(metadata.getClassName());
            }
            return new PackageJustTestScan(
                    new ArrayList<String>(abstractNames),
                    new ArrayList<String>(concreteNames));
        } catch (Exception e) {
            throw new ExtensionConfigurationException(
                    "[JustTest] Failed to scan @JustTest classes in package "
                            + displayPackageName(packageName),
                    e);
        }
    }

    private boolean hasJustTestMarker(MetadataReader reader) {
        return reader.getAnnotationMetadata().hasAnnotation(JustTestMarker.class.getName())
                || reader.getAnnotationMetadata().hasMetaAnnotation(JustTestMarker.class.getName());
    }

    private boolean hasDirectJustTestMarker(Class<?> testClass) {
        return AnnotatedElementUtils.isAnnotated(testClass, JustTestMarker.class);
    }

    private static String displayPackageName(String packageName) {
        return packageName.isEmpty() ? "<default>" : packageName;
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
                    "[JustTest] Failed to discover YAML cases under " + caseRoot, e);
        }
    }

    private CaseContext toCaseContext(String caseName, String caseRoot) {
        String casePath = joinPath(caseRoot, caseName);
        Map<String, Object> data = DataSetLoader.parseYamlByPath(casePath, "request.yaml");
        Map<String, Object> params = data != null ? data : Collections.emptyMap();
        log.debug("[JustTest] Discovered case: {} (path: {})", caseName, casePath);
        return new CaseContext(caseName, casePath, params);
    }

    private String joinPath(String parent, String child) {
        return parent.isEmpty() ? child : parent + "/" + child;
    }

    private static final class PackageJustTestScan {
        private final List<String> abstractClassNames;
        private final List<String> concreteClassNames;

        private PackageJustTestScan(List<String> abstractClassNames, List<String> concreteClassNames) {
            this.abstractClassNames = abstractClassNames;
            this.concreteClassNames = concreteClassNames;
        }
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
            return Collections.<Extension>singletonList(new JustTestExtension(caseContext));
        }
    }
}

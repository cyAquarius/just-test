package com.just.test.smarttest.context;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.loader.DataSetLoader;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * 扫描测试类同包下的 case 子目录，为每个 case 生成一个 {@link CaseContext} 参数。
 *
 * <p>判定规则：子目录中包含至少一个 {@code .yaml} 文件才算有效 case 目录。</p>
 *
 * <p>目录结构示例：</p>
 * <pre>
 * ManualMatchTest/
 * ├── ManualMatchTest.java        ← 测试类
 * ├── deductBalance/
 * │   ├── prepare.yaml
 * │   ├── expect.yaml
 * │   └── request.yaml
 * └── entityNotFound/
 *     ├── prepare.yaml
 *     └── expect_exception.yaml
 * </pre>
 */
public class CaseArgumentsProvider implements ArgumentsProvider {

    private static final Logger log = LoggerFactory.getLogger(CaseArgumentsProvider.class);

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
        Class<?> testClass = context.getRequiredTestClass();
        String packagePath = testClass.getPackage().getName().replace('.', '/');
        CaseSource annotation = context.getRequiredTestMethod().getAnnotation(CaseSource.class);
        String configuredRoot = annotation == null ? "" : annotation.value().trim();
        String caseRoot = configuredRoot.isEmpty()
                ? packagePath + "/" + testClass.getSimpleName()
                : packagePath + "/" + configuredRoot;

        Set<String> caseNames = findCaseNames(caseRoot);
        if (caseNames.isEmpty() && configuredRoot.isEmpty()) {
            caseRoot = packagePath;
            caseNames = findCaseNames(caseRoot);
        }
        if (caseNames.isEmpty()) {
            log.error("[SmartTest] No YAML case directories found: testClass={}, caseRoot={}",
                    testClass.getName(), caseRoot);
            return Stream.empty();
        }

        final String resolvedRoot = caseRoot;
        return caseNames.stream()
                .map(caseName -> toCaseContext(caseName, resolvedRoot))
                .map(Arguments::of);
    }

    private Set<String> findCaseNames(String caseRoot) throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        List<Resource> resources = new ArrayList<>();
        Collections.addAll(resources, resolver.getResources("classpath*:" + caseRoot + "/*/*.yaml"));
        Collections.addAll(resources, resolver.getResources("classpath*:" + caseRoot + "/*/*.yml"));

        Set<String> caseNames = new TreeSet<>();
        String marker = caseRoot + "/";
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
    }

    private CaseContext toCaseContext(String caseName, String caseRoot) {
        String casePath = caseRoot + "/" + caseName;

        // 尝试加载 request.yaml 到 params
        Map<String, Object> params = loadRequestYaml(casePath);

        log.debug("[SmartTest] Discovered case: {} (path: {})", caseName, casePath);
        return new CaseContext(caseName, casePath, params);
    }

    private Map<String, Object> loadRequestYaml(String casePath) {
        Map<String, Object> data = DataSetLoader.parseYamlByPath(casePath, "request.yaml");
        return data != null ? data : Collections.emptyMap();
    }
}

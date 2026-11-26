package com.just.test.internal.verifier;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.just.test.internal.loader.DataSetLoader;
import com.just.test.internal.matcher.BuiltInMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 返回值验证器（ACTS 2.0 风格）。从测试类同包加载 response.yaml，与方法返回值比对。
 *
 * <p>复用与 DB 验证相同的 bracket flag 体系：</p>
 * <ul>
 *   <li>{@code [N]} — 跳过</li>
 *   <li>{@code [R]} — 正则匹配</li>
 *   <li>{@code [A]} — 非空断言</li>
 *   <li>{@code [D]}/{@code [D60]} — 实际时间距当前时刻 N 秒内（YAML 期望值不比较）</li>
 *   <li>{@code [J]} — JSON 结构比较</li>
 *   <li>{@code [C]} — List 元素定位键（无序匹配）</li>
 *   <li>无 flag — 精确断言（Y）</li>
 * </ul>
 */
public class ResultVerifier {

    private static final Logger log = LoggerFactory.getLogger(ResultVerifier.class);

    /**
     * 从 casePath 加载 response.yaml 并校验方法返回值。
     * 支持返回值为对象（Map）或列表（List）。
     *
     * @param actual   方法返回值
     * @param casePath classpath 相对路径（如 "com/example/.../deductBalance"）
     * @return 验证失败的详细信息列表，空列表表示全部通过
     */
    @SuppressWarnings("unchecked")
    public static List<String> verify(Object actual, String casePath) {
        Object expectRaw = DataSetLoader.parseYamlRaw(casePath, "response.yaml");
        if (expectRaw == null) {
            return new ArrayList<>();
        }
        BuiltInMatchers.requireKnownFlags(
                expectRaw, BuiltInMatchers.FlagStage.RESPONSE,
                DataSetLoader.stageLocation(casePath, "response.yaml"));

        // 顶层 List — 逐元素比对
        if (expectRaw instanceof List) {
            return verifyList(actual, (List<Object>) expectRaw, "result");
        }

        if (expectRaw instanceof Map) {
            Map<String, Object> expectMap = (Map<String, Object>) expectRaw;
            Map<String, Object> fields = new LinkedHashMap<>();
            List<String> failures = new ArrayList<>();
            for (Map.Entry<String, Object> entry : expectMap.entrySet()) {
                String key = entry.getKey();
                String flag = BuiltInMatchers.extractFlag(key);
                if (flag == null || !BuiltInMatchers.extractFieldName(key).isEmpty()) {
                    fields.put(key, entry.getValue());
                } else if (BuiltInMatchers.FLAG_N.equals(flag)) {
                    return failures;
                } else {
                    String failure = BuiltInMatchers.assertMatcherFlag(flag, entry.getValue(), actual, "result");
                    if (!failure.isEmpty()) {
                        failures.add(failure);
                    }
                }
            }
            if (failures.isEmpty() || actual != null) {
                failures.addAll(verifyFromMap(actual, fields));
            }
            return failures;
        }

        // 简单类型 — 直接比较
        List<String> failures = new ArrayList<>();
        compareSimple(expectRaw, actual, "result", failures);
        return failures;
    }

    /**
     * 校验 List 返回值。支持有序（默认）和无序（[C] flag 定位）两种模式。
     */
    @SuppressWarnings("unchecked")
    private static List<String> verifyList(Object actual, List<Object> expectList, String path) {
        List<String> failures = new ArrayList<>();

        if (!(actual instanceof List)) {
            List<Object> converted = toList(actual);
            if (converted == null) {
                failures.add(String.format("[%s]: expected List but got %s",
                        path, actual == null ? "null" : actual.getClass().getSimpleName()));
                return failures;
            }
            actual = converted;
        }

        List<Object> actualList = (List<Object>) actual;
        boolean unordered = hasKeyFlag(expectList);
        if (unordered && !validateUnorderedExpectList(expectList, path, failures)) {
            return failures;
        }
        if (expectList.size() != actualList.size()) {
            failures.add(String.format("[%s]: expected list size %d but got %d",
                    path, expectList.size(), actualList.size()));
            return failures;
        }

        // 检测是否有 [C] flag → 无序匹配模式
        if (unordered) {
            verifyListUnordered(actualList, expectList, path, failures);
        } else {
            verifyListOrdered(actualList, expectList, path, failures);
        }

        return failures;
    }

    /**
     * 有序 List 比较（按下标逐一比对，向后兼容）。
     */
    @SuppressWarnings("unchecked")
    private static void verifyListOrdered(List<Object> actualList, List<Object> expectList,
                                          String path, List<String> failures) {
        for (int i = 0; i < expectList.size(); i++) {
            Object expectItem = expectList.get(i);
            Object actualItem = actualList.get(i);
            compareListItem(expectItem, actualItem, path + "[" + i + "]", failures);
        }
    }

    /**
     * 无序 List 比较（按 [C] flag 字段定位匹配元素）。
     */
    @SuppressWarnings("unchecked")
    private static void verifyListUnordered(List<Object> actualList, List<Object> expectList,
                                            String path, List<String> failures) {
        Set<Integer> matchedIndexes = new LinkedHashSet<>();

        for (int ei = 0; ei < expectList.size(); ei++) {
            Map<String, Object> expectMap = (Map<String, Object>) expectList.get(ei);
            Map<String, Object> keyFields = extractKeyFields(expectMap);

            int matchedIdx = -1;
            for (int ai = 0; ai < actualList.size(); ai++) {
                Map<String, Object> actualMap = toMap(actualList.get(ai));
                if (actualMap != null && matchesKeyFields(actualMap, keyFields)) {
                    if (matchedIdx >= 0) {
                        matchedIdx = -2;
                        break;
                    }
                    matchedIdx = ai;
                }
            }

            if (matchedIdx == -2 || matchedIndexes.contains(matchedIdx)) {
                failures.add(String.format("[%s]: ambiguous [C] key %s", path, keyFields));
            } else if (matchedIdx == -1) {
                failures.add(String.format("[%s]: no matching element found for key %s", path, keyFields));
            } else {
                matchedIndexes.add(matchedIdx);
                Map<String, Object> actualMap = toMap(actualList.get(matchedIdx));
                compareMap(actualMap, expectMap, path + "[key=" + keyFields.values() + "]", failures);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean validateUnorderedExpectList(List<Object> expectList, String path,
                                                       List<String> failures) {
        boolean valid = true;
        for (int i = 0; i < expectList.size(); i++) {
            Object item = expectList.get(i);
            if (!(item instanceof Map)) {
                failures.add(String.format(
                        "[%s[%d]]: every expected item must be an object with at least one [C] field "
                                + "when unordered list matching is enabled", path, i));
                valid = false;
                continue;
            }
            if (extractKeyFields((Map<String, Object>) item).isEmpty()) {
                failures.add(String.format(
                        "[%s[%d]]: every expected item must declare at least one [C] field "
                                + "when unordered list matching is enabled", path, i));
                valid = false;
            }
        }
        return valid;
    }

    /**
     * 检测 expect List 中是否有 Map 元素包含 [C] flag。
     */
    private static boolean hasKeyFlag(List<Object> expectList) {
        for (Object item : expectList) {
            if (item instanceof Map) {
                for (Object key : ((Map<?, ?>) item).keySet()) {
                    String flag = BuiltInMatchers.extractFlag(key.toString());
                    if (BuiltInMatchers.FLAG_C.equals(flag)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 从 expect Map 中提取 [C] flag 字段作为定位键。
     */
    private static Map<String, Object> extractKeyFields(Map<String, Object> expectMap) {
        Map<String, Object> keys = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : expectMap.entrySet()) {
            String flag = BuiltInMatchers.extractFlag(entry.getKey());
            if (BuiltInMatchers.FLAG_C.equals(flag)) {
                String fieldName = BuiltInMatchers.extractFieldName(entry.getKey());
                if (!fieldName.isEmpty()) {
                    keys.put(fieldName, entry.getValue());
                }
            }
        }
        return keys;
    }

    /**
     * 判断 actual Map 是否匹配所有 key 字段。
     */
    private static boolean matchesKeyFields(Map<String, Object> actualMap, Map<String, Object> keyFields) {
        for (Map.Entry<String, Object> kf : keyFields.entrySet()) {
            Object actualVal = actualMap.get(kf.getKey());
            String actualStr = actualVal == null ? null : ExactComparison.normalizeActual(actualVal);
            Object expected = kf.getValue();
            if (!actualMap.containsKey(kf.getKey()) || (!ExactComparison.decimalScalesMatch(expected, actualVal)
                    && !Objects.equals(expected == null ? null : expected.toString(), actualStr))) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static void compareListItem(Object expectItem, Object actualItem,
                                        String path, List<String> failures) {
        if (expectItem instanceof Map && actualItem instanceof Map) {
            compareMap((Map<String, Object>) actualItem,
                    (Map<String, Object>) expectItem, path, failures);
        } else if (expectItem instanceof Map) {
            Map<String, Object> actualItemMap = toMap(actualItem);
            if (actualItemMap != null) {
                compareMap(actualItemMap, (Map<String, Object>) expectItem, path, failures);
            } else {
                failures.add(String.format("[%s]: expected object but got <%s>", path, actualItem));
            }
        } else {
            compareSimple(expectItem, actualItem, path, failures);
        }
    }

    private static List<String> verifyFromMap(Object actual, Map<String, Object> expectData) {
        List<String> failures = new ArrayList<>();

        if (expectData == null || expectData.isEmpty()) {
            return failures;
        }

        Map<String, Object> actualMap = toMap(actual);
        if (actualMap == null) {
            failures.add(actual == null
                    ? "[response]: actual is null but expected non-null result"
                    : "[response]: expected a mapping but could not convert actual "
                            + actual.getClass().getName() + " to a mapping");
            return failures;
        }

        compareMap(actualMap, expectData, "result", failures);
        return failures;
    }

    /**
     * 递归比较 Map 结构，支持嵌套对象和 List。
     */
    @SuppressWarnings("unchecked")
    private static void compareMap(Map<String, Object> actualMap, Map<String, Object> expectMap,
                                   String path, List<String> failures) {
        for (Map.Entry<String, Object> entry : expectMap.entrySet()) {
            String rawKey = entry.getKey();
            Object expectValue = entry.getValue();

            String flag = BuiltInMatchers.extractFlag(rawKey);
            String fieldName = BuiltInMatchers.extractFieldName(rawKey);

            String fieldPath = path + "." + fieldName;

            if (BuiltInMatchers.FLAG_N.equals(flag)) {
                continue;
            }

            // [C] flag 在 Map 比较中仅作为定位键，跳过断言
            if (BuiltInMatchers.FLAG_C.equals(flag)) {
                continue;
            }

            Object actualValue = actualMap == null ? null : actualMap.get(fieldName);

            // A/R/D/J — 统一 matcher flag 分发
            String matcherResult = BuiltInMatchers.assertMatcherFlag(flag, expectValue, actualValue, fieldPath);
            if (matcherResult != null) {
                if (!matcherResult.isEmpty()) {
                    failures.add(matcherResult);
                }
                continue;
            }

            // 嵌套 Map — 递归
            if (expectValue instanceof Map) {
                Map<String, Object> actualChild = null;
                if (actualValue instanceof Map) {
                    actualChild = (Map<String, Object>) actualValue;
                } else if (actualValue != null) {
                    actualChild = toMap(actualValue);
                }
                if (actualChild == null) {
                    failures.add(String.format("[%s]: expected nested object but got null", fieldPath));
                } else {
                    compareMap(actualChild, (Map<String, Object>) expectValue, fieldPath, failures);
                }
                continue;
            }

            // 嵌套 List — 委托 verifyList
            if (expectValue instanceof List) {
                failures.addAll(verifyList(actualValue, (List<Object>) expectValue, fieldPath));
                continue;
            }

            // Y（默认）— 精确匹配。BigDecimal 标度与 Timestamp 尾部 .0 走 ExactComparison。
            compareSimple(expectValue, actualValue, fieldPath, failures);
        }
    }

    private static void compareSimple(Object expectValue, Object actualValue,
                                      String path, List<String> failures) {
        if (ExactComparison.decimalScalesMatch(expectValue, actualValue)) {
            return;
        }
        String expectedStr = expectValue == null ? null : expectValue.toString();
        String actualStr = actualValue == null ? null : ExactComparison.normalizeActual(actualValue);
        if (expectedStr == null && actualStr != null) {
            failures.add(String.format("[%s]: expected null but got <%s>", path, actualStr));
        } else if (expectedStr != null && !expectedStr.equals(actualStr)) {
            failures.add(String.format("[%s]: expected <%s> but got <%s>", path, expectedStr, actualStr));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        try {
            String json = JSON.toJSONString(obj, JSONWriter.Feature.FieldBased);
            return JSON.parseObject(json, Map.class);
        } catch (Exception e) {
            log.warn("[JustTest] Failed to convert object to Map: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> toList(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof List) {
            return (List<Object>) obj;
        }
        try {
            // 与 toMap 一样按字段序列化，没有 getter 的 POJO 在两条路径上看到同一组字段。
            String json = JSON.toJSONString(obj, JSONWriter.Feature.FieldBased);
            return JSON.parseArray(json, Object.class);
        } catch (Exception e) {
            log.warn("[JustTest] Failed to convert object to List: {}", e.getMessage());
            return null;
        }
    }
}

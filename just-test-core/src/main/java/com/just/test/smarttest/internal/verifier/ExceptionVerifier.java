package com.just.test.smarttest.internal.verifier;

import com.just.test.smarttest.internal.loader.DataSetLoader;
import com.just.test.smarttest.internal.matcher.BuiltInMatchers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 异常验证器。从 case 目录加载 expect_exception.yaml，与实际捕获的异常比对。
 *
 * <h3>expect_exception.yaml 格式</h3>
 * <pre>
 * type: com.example.domain.BusinessException
 * message: "发票不存在"
 * </pre>
 *
 * <p>message 支持 bracket flag：</p>
 * <ul>
 *   <li>无 flag / {@code [Y]} — 精确匹配</li>
 *   <li>{@code [R]} — 正则匹配</li>
 *   <li>{@code [A]} — 非空断言（只要 message 不为 null 即通过）</li>
 *   <li>{@code [N]} — 跳过 message 校验</li>
 * </ul>
 *
 * <p>三种校验场景：</p>
 * <ol>
 *   <li>有 expect_exception.yaml + 有异常 → 比对 type 和 message</li>
 *   <li>有 expect_exception.yaml + 无异常 → 失败（期望异常但未抛出）</li>
 *   <li>无 expect_exception.yaml → 跳过（不校验异常）</li>
 * </ol>
 */
public class ExceptionVerifier {

    /**
     * 校验异常是否符合 expect_exception.yaml 的声明。
     *
     * @param actual   实际捕获的异常（可能为 null）
     * @param casePath classpath 相对路径（如 "com/example/.../deductBalance"）
     * @return 验证失败的详细信息列表，空列表表示全部通过
     */
    public static List<String> verify(Throwable actual, String casePath) {
        List<String> failures = new ArrayList<>();

        Map<String, Object> expectData = loadExpectException(casePath);
        if (expectData == null || expectData.isEmpty()) {
            // 无 expect_exception.yaml → 跳过
            return failures;
        }

        // 有声明但无异常
        if (actual == null) {
            failures.add(String.format("[exception]: expected exception [%s] but none was thrown",
                    expectData.get("type")));
            return failures;
        }

        // 校验 type（支持 cause chain 遍历）
        Object expectedType = expectData.get("type");
        Throwable matched = actual;
        if (expectedType != null) {
            matched = findMatchingCause(actual, expectedType.toString());
            if (matched == null) {
                failures.add(String.format("[exception.type]: expected <%s> but got <%s> (cause chain exhausted)",
                        expectedType, actual.getClass().getName()));
                return failures;
            }
        }

        // 校验 message（基于匹配到的异常，支持 flag）
        verifyMessage(matched, expectData, failures);

        return failures;
    }

    /**
     * 判断指定 casePath 下是否存在 expect_exception.yaml。
     */
    public static boolean hasExpectException(String casePath) {
        String resourcePath = casePath + "/expect_exception.yaml";
        return Thread.currentThread().getContextClassLoader().getResource(resourcePath) != null;
    }

    private static void verifyMessage(Throwable actual, Map<String, Object> expectData, List<String> failures) {
        // 查找 message 相关的 key（可能带 flag）
        String messageKey = null;
        for (String key : expectData.keySet()) {
            String fieldName = BuiltInMatchers.extractFieldName(key);
            if ("message".equals(fieldName)) {
                messageKey = key;
                break;
            }
        }

        if (messageKey == null) {
            // 没有声明 message → 跳过 message 校验
            return;
        }

        String flag = BuiltInMatchers.extractFlag(messageKey);
        Object expectedValue = expectData.get(messageKey);
        String actualMessage = actual.getMessage();

        if (BuiltInMatchers.FLAG_N.equals(flag)) {
            return;
        }

        String matcherResult = BuiltInMatchers.assertMatcherFlag(
                flag, expectedValue, actualMessage, "exception.message");
        if (matcherResult != null) {
            if (!matcherResult.isEmpty()) {
                failures.add(matcherResult);
            }
            return;
        }

        String expectedStr = expectedValue == null ? null : expectedValue.toString();
        if (expectedStr == null && actualMessage != null) {
            failures.add(String.format("[exception.message]: expected null but got <%s>", actualMessage));
        } else if (expectedStr != null && !expectedStr.equals(actualMessage)) {
            failures.add(String.format("[exception.message]: expected <%s> but got <%s>",
                    expectedStr, actualMessage));
        }
    }

    /**
     * 沿 cause chain 查找类型匹配的异常。
     * 先检查顶层异常，再逐层遍历 getCause()，最多 10 层防止循环引用。
     */
    private static Throwable findMatchingCause(Throwable throwable, String expectedTypeName) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 10) {
            if (current.getClass().getName().equals(expectedTypeName)) {
                return current;
            }
            Throwable next = current.getCause();
            if (next == current) {
                break; // 自引用保护
            }
            current = next;
            depth++;
        }
        return null;
    }

    private static Map<String, Object> loadExpectException(String casePath) {
        return DataSetLoader.parseYamlByPath(casePath, "expect_exception.yaml");
    }
}

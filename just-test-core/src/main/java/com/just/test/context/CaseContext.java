package com.just.test.context;

import com.alibaba.fastjson2.JSON;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Case 运行时上下文，承载单个测试 case 的输入参数、执行结果和异常。
 *
 * <p>由 {@code @CaseSource} 为每个 invocation 创建，框架将其注入测试方法及标准
 * JUnit 生命周期方法，并在执行过程中填充结果和异常。</p>
 *
 * <p>{@link #toString()} 返回 caseName；JUnit invocation 也使用 caseName 作为显示名。</p>
 */
public class CaseContext {

    private final String caseName;
    private final String casePath;
    private final Map<String, Object> params;
    private final String executionId = UUID.randomUUID().toString();
    private Object result;
    private Throwable exception;

    /**
     * @param caseName case 目录名（如 "deductBalance"）
     * @param casePath classpath 相对路径（如 "com/example/application/.../calculate"）
     */
    public CaseContext(String caseName, String casePath) {
        this(caseName, casePath, Collections.emptyMap());
    }

    public CaseContext(String caseName, String casePath, Map<String, Object> params) {
        this.caseName = caseName;
        this.casePath = casePath;
        this.params = params != null ? params : Collections.emptyMap();
    }

    public String getCaseName() {
        return caseName;
    }

    public String getCasePath() {
        return casePath;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public String getExecutionId() {
        return executionId;
    }

    // ---- 类型安全的参数取值 ----

    public String getString(String key) {
        Object value = getParams().get(key);
        return value == null ? null : value.toString();
    }

    public Long getLong(String key) {
        return getObject(key, Long.class);
    }

    public Integer getInt(String key) {
        return getObject(key, Integer.class);
    }

    public <T> T getObject(String key, Class<T> type) {
        Object value = getParams().get(key);
        if (value == null) {
            return null;
        }
        try {
            return convertElement(value, type);
        } catch (RuntimeException e) {
            throw conversionFailure(key, type.getSimpleName(), e);
        }
    }

    /**
     * 取 List 参数，自动做元素类型转换。
     *
     * <pre>
     * # request.yaml
     * lineDetailIds:
     *   - 101
     *   - 102
     *
     * # Java
     * List&lt;Long&gt; ids = ctx.getList("lineDetailIds", Long.class);
     * </pre>
     *
     * <p>支持元素类型：Long、Integer、String、BigDecimal、Java Bean（Map → Fastjson 转换）。</p>
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getList(String key, Class<T> elementType) {
        Object value = getParams().get(key);
        if (value == null) {
            return Collections.emptyList();
        }
        try {
            if (!(value instanceof List)) {
                // 单值兼容：自动包装为单元素 List
                return Collections.singletonList(convertElement(value, elementType));
            }
            return ((List<?>) value).stream()
                    .map(item -> convertElement(item, elementType))
                    .collect(Collectors.toList());
        } catch (RuntimeException e) {
            throw conversionFailure(key, "List<" + elementType.getSimpleName() + ">", e);
        }
    }

    /**
     * 取嵌套 Map 参数（YAML 对象）。
     *
     * <pre>
     * # request.yaml
     * matchRequest:
     *   expenseTypeId: 1
     *   remark: "批量匹配"
     *
     * # Java
     * Map&lt;String, Object&gt; req = ctx.getMap("matchRequest");
     * </pre>
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMap(String key) {
        Object value = getParams().get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private <T> T convertElement(Object item, Class<T> type) {
        if (item == null) {
            return null;
        }
        if (type.isInstance(item)) {
            return type.cast(item);
        }
        // 数值类型转换
        if (type == Long.class) {
            return (T) (item instanceof Number
                    ? Long.valueOf(exactDecimal((Number) item).longValueExact())
                    : Long.valueOf(item.toString()));
        }
        if (type == Integer.class) {
            return (T) (item instanceof Number
                    ? Integer.valueOf(exactDecimal((Number) item).intValueExact())
                    : Integer.valueOf(item.toString()));
        }
        if (type == BigDecimal.class) {
            return (T) new BigDecimal(item.toString());
        }
        if (type == Double.class && (item instanceof Number || item instanceof String)) {
            return (T) (item instanceof Number
                    ? Double.valueOf(((Number) item).doubleValue())
                    : Double.valueOf((String) item));
        }
        if (type == Boolean.class) {
            if (item instanceof String
                    && ("true".equalsIgnoreCase((String) item) || "false".equalsIgnoreCase((String) item))) {
                return (T) Boolean.valueOf((String) item);
            }
            throw new IllegalArgumentException("Expected a Boolean or a true/false string: " + item);
        }
        if (type == String.class) {
            return (T) item.toString();
        }
        // Map → Java Bean（List<对象> 或嵌套 YAML 对象场景）
        if (item instanceof Map) {
            try {
                String json = JSON.toJSONString(item);
                return JSON.parseObject(json, type);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        String.format("Cannot convert to %s: %s", type.getSimpleName(), e.getMessage()), e);
            }
        }
        return type.cast(item);
    }

    private static BigDecimal exactDecimal(Number number) {
        if (number instanceof Double || number instanceof Float) {
            return new BigDecimal(number.doubleValue());
        }
        return number instanceof BigDecimal ? (BigDecimal) number : new BigDecimal(number.toString());
    }

    private IllegalArgumentException conversionFailure(String key, String target, RuntimeException cause) {
        return new IllegalArgumentException(String.format(
                "[JustTest] Cannot convert parameter '%s' to %s in case '%s' (%s): %s",
                key, target, caseName, casePath, cause.getMessage()), cause);
    }

    // ---- 执行结果 ----

    public Object getResult() {
        return result;
    }

    /**
     * 写入本 case 的业务返回值，供 {@code response.yaml} / {@code verifyResult} 使用。
     *
     * <p>框架不会从 {@code @CaseSource} 方法的返回值自动采集结果；
     * 需要结果断言时必须显式调用本方法。</p>
     */
    public void setResult(Object result) {
        this.result = result;
    }

    public Throwable getException() {
        return exception;
    }

    public void setException(Throwable exception) {
        this.exception = exception;
    }

    /**
     * JUnit 报告中的显示名。
     */
    @Override
    public String toString() {
        return caseName;
    }
}

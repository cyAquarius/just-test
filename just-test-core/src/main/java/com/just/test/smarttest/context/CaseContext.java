package com.just.test.smarttest.context;

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
        Object value = params.get(key);
        return value == null ? null : value.toString();
    }

    public Long getLong(String key) {
        Object value = params.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.valueOf(value.toString());
    }

    public Integer getInt(String key) {
        Object value = params.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.valueOf(value.toString());
    }

    @SuppressWarnings("unchecked")
    public <T> T getObject(String key, Class<T> type) {
        Object value = params.get(key);
        if (value == null) {
            return null;
        }
        return convertElement(value, type);
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
        Object value = params.get(key);
        if (value == null) {
            return Collections.emptyList();
        }
        if (!(value instanceof List)) {
            // 单值兼容：自动包装为单元素 List
            return Collections.singletonList(convertElement(value, elementType));
        }
        return ((List<?>) value).stream()
                .map(item -> convertElement(item, elementType))
                .collect(Collectors.toList());
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
        Object value = params.get(key);
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
        if (type == Long.class && item instanceof Number) {
            return (T) Long.valueOf(((Number) item).longValue());
        }
        if (type == Integer.class && item instanceof Number) {
            return (T) Integer.valueOf(((Number) item).intValue());
        }
        if (type == BigDecimal.class) {
            return (T) new BigDecimal(item.toString());
        }
        if (type == Double.class && item instanceof Number) {
            return (T) Double.valueOf(((Number) item).doubleValue());
        }
        if (type == Boolean.class) {
            return (T) Boolean.valueOf(item.toString());
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

    // ---- 执行结果 ----

    public Object getResult() {
        return result;
    }

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

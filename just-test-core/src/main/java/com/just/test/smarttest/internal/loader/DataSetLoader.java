package com.just.test.smarttest.internal.loader;

import com.just.test.smarttest.internal.matcher.BuiltInMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * YAML 数据集加载器（ACTS 2.0 风格）。
 *
 * <p>从 classpath 上的 case 目录加载 YAML（{@code casePath/prepare.yaml} 等），
 * 解析 prepare 块并将数据 INSERT 到 H2。</p>
 */
public class DataSetLoader {

    private static final Logger log = LoggerFactory.getLogger(DataSetLoader.class);
    private static final Pattern SAFE_SQL_IDENTIFIER = Pattern.compile("^[a-zA-Z0-9_]+$");
    private static final String ALL_TABLES_SQL =
            "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES "
                    + "WHERE TABLE_SCHEMA = CURRENT_SCHEMA() AND TABLE_TYPE = 'BASE TABLE'";

    /**
     * 校验表名只包含安全字符（字母、数字、下划线）。
     */
    public static void validateTableName(String tableName) {
        validateSqlIdentifier(tableName, "table name");
    }

    /**
     * 校验列名只包含安全字符（字母、数字、下划线）。
     */
    public static void validateColumnName(String columnName) {
        validateSqlIdentifier(columnName, "column name");
    }

    private static void validateSqlIdentifier(String name, String kind) {
        if (name == null || !SAFE_SQL_IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "[SmartTest] Invalid " + kind + ": " + name + " — only [a-zA-Z0-9_] allowed");
        }
    }

    /**
     * 从 casePath 加载 prepare.yaml 并灌入数据库。
     *
     * @param jdbcTemplate H2 JdbcTemplate
     * @param casePath     classpath 相对路径（如 "com/example/.../deductBalance"）
     */
    public static void load(JdbcTemplate jdbcTemplate, String casePath) {
        if (casePath == null || casePath.isEmpty()) {
            return;
        }
        Map<String, Object> yamlData = parseYamlByPath(casePath, "prepare.yaml");
        if (yamlData == null) {
            return;
        }
        loadFromMap(jdbcTemplate, yamlData, casePath);
    }

    /**
     * 清空当前 Schema 的全部业务表。
     *
     * <p>不是 case 主路径：每个 case 使用独立内存库，框架不靠 TRUNCATE 做隔离。
     * 仅契约测试或需要在同一连接上重置表数据时调用。</p>
     *
     * <p>不能以 H2 的行数估算决定是否清理：它是性能统计，不是隔离正确性信号。</p>
     */
    public static void cleanTables(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            List<String> tables = new ArrayList<>();
            try (Statement query = connection.createStatement();
                 ResultSet resultSet = query.executeQuery(ALL_TABLES_SQL)) {
                while (resultSet.next()) {
                    String table = resultSet.getString(1);
                    validateTableName(table);
                    tables.add(table);
                }
            }
            try (Statement cleanup = connection.createStatement()) {
                cleanup.execute("SET REFERENTIAL_INTEGRITY FALSE");
                try {
                    for (String table : tables) {
                        // H2 的 TRUNCATE 会自动恢复该 identity 列在 DDL 中声明的初始值。
                        cleanup.addBatch("TRUNCATE TABLE " + table);
                    }
                    cleanup.executeBatch();
                } finally {
                    cleanup.execute("SET REFERENTIAL_INTEGRITY TRUE");
                }
            }
            log.debug("[SmartTest] Truncated {} tables in one batch", tables.size());
            return null;
        });
    }

    /**
     * 从已解析的 YAML Map 加载数据到数据库。
     */
    @SuppressWarnings("unchecked")
    static void loadFromMap(JdbcTemplate jdbcTemplate, Map<String, Object> yamlData, String source) {
        if (yamlData == null || yamlData.isEmpty()) {
            log.debug("[SmartTest] No prepare data from: {}", source);
            return;
        }

        for (Map.Entry<String, Object> entry : yamlData.entrySet()) {
            String tableName = entry.getKey();
            validateTableName(tableName);
            Object value = entry.getValue();
            if (!(value instanceof List)) {
                continue;
            }
            List<Map<String, Object>> rows = (List<Map<String, Object>>) value;
            if (rows.isEmpty()) {
                continue;
            }
            insertRows(jdbcTemplate, tableName, rows);
        }
    }

    /**
     * 从 casePath 加载指定 YAML 文件。
     *
     * @param casePath classpath 相对路径（如 "com/example/.../deductBalance"）
     * @param fileName 文件名（如 prepare.yaml、expect.yaml）
     * @return YAML 数据 Map，文件不存在返回 null
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseYamlByPath(String casePath, String fileName) {
        Object raw = parseYamlRaw(casePath, fileName);
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof Map)) {
            throw new IllegalArgumentException(
                    "[SmartTest] YAML must be a mapping: " + casePath + "/" + fileName);
        }
        return (Map<String, Object>) raw;
    }

    /**
     * 解析 YAML 文件，返回原始对象（可能是 Map 或 List）。
     * 用于 response.yaml 等可能为顶层 List 的场景。
     *
     * @param casePath classpath 相对路径
     * @param fileName 文件名
     * @return YAML 原始对象，文件不存在返回 null
     */
    public static Object parseYamlRaw(String casePath, String fileName) {
        String resourcePath = casePath + "/" + fileName;
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                log.debug("[SmartTest] YAML file not found: {}", resourcePath);
                return null;
            }
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Object raw = yaml.load(new InputStreamReader(is, StandardCharsets.UTF_8));
            return normalizeYamlKeys(raw);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("[SmartTest] Failed to parse YAML: " + resourcePath, e);
        }
    }

    /**
     * 递归规范化 YAML 解析结果中的 Map key。
     *
     * <p>SnakeYAML 会把 {@code [A]:} 解析为 flow sequence（key 类型为 {@code List}），
     * 需要还原为字符串 {@code "[A]"}，以匹配 SmartTest Flag 体系。</p>
     */
    @SuppressWarnings("unchecked")
    static Object normalizeYamlKeys(Object obj) {
        if (obj instanceof Map) {
            Map<Object, Object> raw = (Map<Object, Object>) obj;
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<Object, Object> entry : raw.entrySet()) {
                String key = toStringKey(entry.getKey());
                result.put(key, normalizeYamlKeys(entry.getValue()));
            }
            return result;
        }
        if (obj instanceof List) {
            List<Object> raw = (List<Object>) obj;
            List<Object> result = new ArrayList<>(raw.size());
            for (Object item : raw) {
                result.add(normalizeYamlKeys(item));
            }
            return result;
        }
        return obj;
    }

    /**
     * 将 SnakeYAML 解析出的 key 转为 String。
     * <ul>
     *   <li>{@code List["A"]} → {@code "[A]"}</li>
     *   <li>{@code List["C", "id"]} → {@code "[C,id]"}</li>
     *   <li>其他类型 → {@code toString()}</li>
     * </ul>
     */
    private static String toStringKey(Object key) {
        if (key instanceof String) {
            return (String) key;
        }
        if (key instanceof List) {
            List<?> list = (List<?>) key;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(list.get(i));
            }
            sb.append("]");
            return sb.toString();
        }
        return String.valueOf(key);
    }

    private static void insertRows(JdbcTemplate jdbcTemplate, String tableName, List<Map<String, Object>> rows) {
        int insertedCount = 0;
        for (Map<String, Object> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            StringJoiner columns = new StringJoiner(", ");
            StringJoiner placeholders = new StringJoiner(", ");
            List<Object> values = new ArrayList<>();

            for (Map.Entry<String, Object> col : row.entrySet()) {
                String rawKey = col.getKey();
                String flag = BuiltInMatchers.extractFlag(rawKey);
                String fieldName = BuiltInMatchers.extractFieldName(rawKey);

                // N flag — prepare 阶段跳过该列
                if (BuiltInMatchers.FLAG_N.equals(flag)) {
                    continue;
                }

                validateColumnName(fieldName);
                columns.add(fieldName);

                // F flag — DB 函数，直接拼入 SQL（如 NOW()）
                if (BuiltInMatchers.FLAG_F.equals(flag)) {
                    placeholders.add(col.getValue() == null ? "NULL" : col.getValue().toString());
                } else {
                    placeholders.add("?");
                    values.add(col.getValue());
                }
            }

            if (columns.length() == 0) {
                continue;
            }

            String sql = String.format("INSERT INTO %s (%s) VALUES (%s)",
                    tableName, columns, placeholders);

            try {
                jdbcTemplate.update(sql, values.toArray());
                insertedCount++;
            } catch (Exception e) {
                throw new RuntimeException(
                        String.format("[SmartTest] Failed to insert into %s: %s", tableName, e.getMessage()), e);
            }
        }
        log.debug("[SmartTest] Inserted {} rows into {}", insertedCount, tableName);
    }
}

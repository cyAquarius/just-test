package com.just.test.smarttest.verifier;

import com.just.test.smarttest.loader.DataSetLoader;
import com.just.test.smarttest.matcher.BuiltInMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据集验证器（ACTS 2.0 风格）。从测试类同包加载 expect.yaml，与数据库实际数据比对。
 *
 * <h3>字段角色由 bracket flag 决定</h3>
 * <ul>
 *   <li>{@code [C]} — WHERE 定位条件</li>
 *   <li>{@code [N]} — 跳过验证</li>
 *   <li>{@code [CN]} — 定位后断言行不存在</li>
 *   <li>{@code [R]} — 正则匹配</li>
 *   <li>{@code [A]} — 非空断言</li>
 *   <li>{@code [D]} / {@code [D60]} — 日期容差比较</li>
 *   <li>{@code [J]} — JSON 结构比较</li>
 *   <li>无 flag — 精确断言（Y）</li>
 * </ul>
 */
public class DataSetVerifier {

    private static final Logger log = LoggerFactory.getLogger(DataSetVerifier.class);

    /**
     * 从 casePath 加载 expect.yaml 并校验数据库状态。
     *
     * @param jdbcTemplate H2 JdbcTemplate
     * @param casePath     classpath 相对路径（如 "com/example/.../deductBalance"）
     * @return 验证失败的详细信息列表，空列表表示全部通过
     */
    public static List<String> verify(JdbcTemplate jdbcTemplate, String casePath) {
        Map<String, Object> yamlData = DataSetLoader.parseYamlByPath(casePath, "expect.yaml");
        return verifyFromMap(jdbcTemplate, yamlData);
    }

    /**
     * 从已解析的 YAML Map 校验数据库状态。
     */
    @SuppressWarnings("unchecked")
    static List<String> verifyFromMap(JdbcTemplate jdbcTemplate, Map<String, Object> yamlData) {
        List<String> failures = new ArrayList<>();

        if (yamlData == null || yamlData.isEmpty()) {
            return failures;
        }

        for (Map.Entry<String, Object> entry : yamlData.entrySet()) {
            String tableName = entry.getKey();
            DataSetLoader.validateTableName(tableName);
            Object value = entry.getValue();
            if (!(value instanceof List)) {
                continue;
            }
            List<Map<String, Object>> expectedRows = (List<Map<String, Object>>) value;
            if (expectedRows.isEmpty()) {
                continue;
            }
            verifyTable(jdbcTemplate, tableName, expectedRows, failures);
        }

        return failures;
    }

    private static void verifyTable(JdbcTemplate jdbcTemplate, String tableName,
                                    List<Map<String, Object>> expectedRows, List<String> failures) {
        for (int i = 0; i < expectedRows.size(); i++) {
            Map<String, Object> expectedRow = expectedRows.get(i);
            if (expectedRow == null || expectedRow.isEmpty()) {
                continue;
            }
            verifyRow(jdbcTemplate, tableName, expectedRow, i, failures);
        }
    }

    private static void verifyRow(JdbcTemplate jdbcTemplate, String tableName,
                                  Map<String, Object> rawRow, int rowIndex, List<String> failures) {
        List<ParsedField> fields = parseFields(rawRow);

        boolean assertNotExist = fields.stream()
                .anyMatch(f -> BuiltInMatchers.FLAG_CN.equals(f.flag));

        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();
        buildWhereClause(fields, where, params);

        List<Map<String, Object>> actualRows;
        if (where.length() == 0) {
            actualRows = jdbcTemplate.queryForList("SELECT * FROM " + tableName);
        } else {
            actualRows = jdbcTemplate.queryForList(
                    "SELECT * FROM " + tableName + " WHERE " + where, params.toArray());
        }

        // CN — 断言行不存在
        if (assertNotExist) {
            if (!actualRows.isEmpty()) {
                failures.add(String.format("[%s] row[%d]: expected row NOT to exist but found %d row(s)",
                        tableName, rowIndex, actualRows.size()));
            }
            return;
        }

        // 断言行存在
        if (actualRows.isEmpty()) {
            failures.add(String.format("[%s] row[%d]: no matching row found in database", tableName, rowIndex));
            dumpTable(jdbcTemplate, tableName, failures);
            return;
        }

        if (actualRows.size() > 1) {
            log.warn("[{}] row[{}]: WHERE matched {} rows, only verifying the first one. " +
                    "Consider adding [C] flag to narrow down.", tableName, rowIndex, actualRows.size());
        }
        Map<String, Object> actualRow = actualRows.get(0);

        // 逐字段验证
        for (ParsedField field : fields) {
            String flag = field.flag;

            if (BuiltInMatchers.FLAG_C.equals(flag) || BuiltInMatchers.FLAG_CN.equals(flag)) {
                continue;
            }
            if (BuiltInMatchers.FLAG_N.equals(flag)) {
                continue;
            }

            Object actualValue = getColumnValue(actualRow, field.fieldName);

            // A/R/D/J — 统一 matcher flag 分发
            String fieldPath = tableName + " row[" + rowIndex + "]." + field.fieldName;
            String matcherResult = BuiltInMatchers.assertMatcherFlag(flag, field.value, actualValue, fieldPath);
            if (matcherResult != null) {
                if (!matcherResult.isEmpty()) {
                    failures.add(matcherResult);
                }
                continue;
            }

            // Y（默认）— 精确匹配
            String expectedStr = field.value == null ? null : field.value.toString();
            String actualStr = actualValue == null ? null : normalizeToString(actualValue);
            if (expectedStr == null && actualStr != null) {
                failures.add(String.format("[%s] row[%d].%s: expected null but got <%s>",
                        tableName, rowIndex, field.fieldName, actualStr));
            } else if (expectedStr != null && !expectedStr.equals(actualStr)) {
                failures.add(String.format("[%s] row[%d].%s: expected <%s> but got <%s>",
                        tableName, rowIndex, field.fieldName, expectedStr, actualStr));
            }
        }
    }

    private static void buildWhereClause(List<ParsedField> fields, StringBuilder where, List<Object> params) {
        // 策略1：C / CN flag 字段
        for (ParsedField f : fields) {
            if (BuiltInMatchers.FLAG_C.equals(f.flag) || BuiltInMatchers.FLAG_CN.equals(f.flag)) {
                appendCondition(where, params, f.fieldName, f.value);
            }
        }
        if (where.length() > 0) {
            return;
        }

        // 兜底：所有无 flag 字段做 WHERE（建议显式使用 [C] flag）
        log.warn("[SmartTest] No [C] flag found in expect row, falling back to all-field WHERE. " +
                "Consider adding [C] flag for explicit row targeting.");
        for (ParsedField f : fields) {
            if (f.flag == null && f.value != null) {
                appendCondition(where, params, f.fieldName, f.value);
            }
        }
    }

    private static void appendCondition(StringBuilder where, List<Object> params,
                                        String fieldName, Object value) {
        if (where.length() > 0) {
            where.append(" AND ");
        }
        if (value == null) {
            where.append(fieldName).append(" IS NULL");
        } else {
            where.append(fieldName).append(" = ?");
            params.add(value);
        }
    }

    private static List<ParsedField> parseFields(Map<String, Object> rawRow) {
        List<ParsedField> result = new ArrayList<>();
        for (Map.Entry<String, Object> entry : rawRow.entrySet()) {
            String rawKey = entry.getKey();
            String flag = BuiltInMatchers.extractFlag(rawKey);
            String fieldName = BuiltInMatchers.extractFieldName(rawKey);
            result.add(new ParsedField(fieldName, flag, entry.getValue()));
        }
        return result;
    }

    private static String normalizeToString(Object value) {
        if (value instanceof java.math.BigDecimal) {
            return ((java.math.BigDecimal) value).toPlainString();
        }
        String str = value.toString();
        if (value instanceof java.sql.Timestamp) {
            // 统一去掉尾部的 .0、.00、.000 等零精度后缀
            return str.replaceAll("\\.0+$", "");
        }
        return str;
    }

    private static Object getColumnValue(Map<String, Object> row, String columnName) {
        Object value = row.get(columnName);
        if (value == null) {
            value = row.get(columnName.toUpperCase());
        }
        if (value == null) {
            value = row.get(columnName.toLowerCase());
        }
        return value;
    }

    static class ParsedField {
        final String fieldName;
        final String flag;
        final Object value;

        ParsedField(String fieldName, String flag, Object value) {
            this.fieldName = fieldName;
            this.flag = flag;
            this.value = value;
        }
    }

    /**
     * 验证失败时 dump 表当前数据（最多 10 行），辅助排查。
     */
    private static void dumpTable(JdbcTemplate jdbcTemplate, String tableName, List<String> failures) {
        try {
            List<Map<String, Object>> allRows = jdbcTemplate.queryForList(
                    "SELECT * FROM " + tableName + " LIMIT 10");
            if (allRows.isEmpty()) {
                failures.add(String.format("  [snapshot] %s is EMPTY", tableName));
            } else {
                failures.add(String.format("  [snapshot] %s has %d row(s):", tableName, allRows.size()));
                for (int i = 0; i < allRows.size(); i++) {
                    failures.add(String.format("    row[%d]: %s", i, allRows.get(i)));
                }
            }
        } catch (Exception e) {
            log.debug("[SmartTest] Failed to dump table {}: {}", tableName, e.getMessage());
        }
    }
}

package com.just.test.internal.h2;

/**
 * H2 自定义函数，兼容 MySQL 特有函数。
 * <p>
 * 通过 {@code CREATE ALIAS} 注册到 H2 数据库，使 mapper XML 中的 MySQL 函数
 * 在 H2 测试环境下可正常执行。
 * <p>
 * 覆盖的 MySQL 函数：
 * <ul>
 *   <li>{@code FIND_IN_SET(str, strList)} — 逗号分隔列表查找，大小写不敏感</li>
 * </ul>
 *
 * <p>注意：{@code IF()} 由 {@link H2MySqlIfInterceptor} 在 MyBatis 层改写为 CASEWHEN()，
 * 不通过 CREATE ALIAS 注册（IF 是 H2 SQL 保留字）。</p>
 *
 * <p>H2 MODE=MySQL 已内置支持 IFNULL、NOW()、LENGTH()、LOCATE()、CONVERT() 等函数，
 * 无需额外注册。</p>
 */
public final class MySqlCompatFunctions {

    private MySqlCompatFunctions() {
    }

    // ==================== FIND_IN_SET ====================

    /**
     * MySQL FIND_IN_SET(str, strList) 兼容实现。比较大小写不敏感，对齐 MySQL 默认 CI 排序规则。
     *
     * <p>相对完整 MySQL 排序规则，仍故意保留这些差异：</p>
     * <ul>
     *   <li>重音敏感：{@code a} 不等于 {@code á}（不做 utf8mb4_0900_ai_ci 的重音不敏感）</li>
     *   <li>尾随空格敏感：{@code a} 不等于 {@code a }（对齐 MySQL 8 默认 NO PAD，而不是旧的 PAD SPACE）</li>
     *   <li>第一个参数中的逗号不按列表项匹配，也不对 SET 列做位运算</li>
     * </ul>
     *
     * @param str     要查找的字符串
     * @param strList 逗号分隔的字符串列表
     * @return 1-based 位置，未找到返回 0，任一参数为 null 返回 null
     */
    public static Integer findInSet(String str, String strList) {
        if (str == null || strList == null) {
            return null;
        }
        String[] parts = strList.split(",", -1);
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equalsIgnoreCase(str)) {
                return i + 1;
            }
        }
        return 0;
    }

}

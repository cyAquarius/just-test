package com.just.test.internal.datasource;

import com.just.test.internal.h2.H2FunctionRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Schema 初始化器。加载 DDL 文件并自动清理 MySQL 特有语法后在 H2 上执行建表。
 *
 * <p>每个 case 仍然使用独立的内存数据库；清理后的 DDL 会缓存，避免后续 case
 * 重复读取资源和清理语法。对 {@link JustTestRoutingDataSource}，默认先从共享
 * template 数据库通过 H2 {@code SCRIPT SIMPLE} 克隆 schema，并缓存 SCRIPT 语句列表，
 * 后续 case 重放该缓存而不再对 template 执行 {@code SCRIPT}；克隆失败时回退到
 * 缓存 DDL 重放。设置 {@code justtest.schema.clone=false} 可禁用克隆并始终使用
 * 缓存 DDL 重放。</p>
 */
public class SchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);
    private static final ConcurrentMap<SchemaCacheKey, List<String>> CLEANED_SCHEMA_CACHE =
            new ConcurrentHashMap<>();
    // MySQL 特有语法的清理正则
    private static final Pattern ENGINE_PATTERN = Pattern.compile("\\s*ENGINE\\s*=\\s*\\w+", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTO_INCREMENT_PATTERN = Pattern.compile("\\s*AUTO_INCREMENT\\s*=\\s*\\d+", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHARSET_PATTERN = Pattern.compile("\\s*(DEFAULT\\s+)?CHARSET\\s*=\\s*\\w+", Pattern.CASE_INSENSITIVE);
    // 列级是 COLLATE name；表级 SHOW CREATE 是 COLLATE=name。词边界避免把 collate_flag 当成排序规则。
    private static final Pattern COLLATE_COLUMN_PATTERN = Pattern.compile(
            "\\s*\\bCOLLATE\\b\\s+\\w+", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLLATE_TABLE_PATTERN = Pattern.compile(
            "\\s*\\bCOLLATE\\b\\s*=\\s*\\w+", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMENT_TABLE_PATTERN = Pattern.compile("\\s*COMMENT\\s*=\\s*'[^']*'", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMENT_COLUMN_PATTERN = Pattern.compile("\\s+COMMENT\\s+'[^']*'", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROW_FORMAT_PATTERN = Pattern.compile("\\s*ROW_FORMAT\\s*=\\s*\\w+", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNSIGNED_PATTERN = Pattern.compile("\\s+UNSIGNED", Pattern.CASE_INSENSITIVE);
    private static final Pattern ON_UPDATE_PATTERN = Pattern.compile("\\s+ON\\s+UPDATE\\s+CURRENT_TIMESTAMP(\\(\\d*\\))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHARACTER_SET_PATTERN = Pattern.compile("\\s+CHARACTER\\s+SET\\s+\\w+", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEFAULT_BIT_PATTERN = Pattern.compile("DEFAULT\\s+b'([01]+)'", Pattern.CASE_INSENSITIVE);
    private static final char SEGMENT_MARK = '\uE000';
    private static final char SEGMENT_MARK_END = '\uE001';

    /**
     * 初始化当前 case 的 schema。每个 case 只会标记并初始化自己的数据库；
     * schema cache、template clone 或缓存 DDL fallback 负责避免重复读取和清理资源。
     *
     * @param jdbcTemplate  H2 JdbcTemplate
     * @param schemaLocations DDL 文件路径（支持 classpath 通配符）
     */
    public static void initialize(JdbcTemplate jdbcTemplate, String... schemaLocations) {
        JustTestRoutingDataSource routingDataSource = resolveRoutingDataSource(jdbcTemplate);
        if (routingDataSource != null && !routingDataSource.beginSchemaInitialization()) {
            return;
        }
        String dbKey = routingDataSource == null ? "external" : routingDataSource.currentDbKey();
        log.debug("[JustTest] Initializing schema for database [{}]", dbKey);

        try {
            List<String> cleanedDdls = getCachedSchema(schemaLocations);
            if (routingDataSource == null) {
                executeCachedDdl(jdbcTemplate, cleanedDdls);
                H2FunctionRegistrar.register(jdbcTemplate);
            } else {
                routingDataSource.initializeCaseSchema(jdbcTemplate, cleanedDdls);
            }
        } catch (Exception e) {
            if (routingDataSource != null) {
                routingDataSource.schemaInitializationFailed();
            }
            throw new RuntimeException("[JustTest] Failed to initialize schema", e);
        }
    }

    private static List<String> getCachedSchema(String... schemaLocations) {
        SchemaCacheKey cacheKey = new SchemaCacheKey(schemaLocations);
        List<String> cached = CLEANED_SCHEMA_CACHE.get(cacheKey);
        if (cached != null) {
            log.debug("[JustTest] Using cached cleaned schema for {}", cacheKey.locations);
            return cached;
        }
        return CLEANED_SCHEMA_CACHE.computeIfAbsent(cacheKey, SchemaInitializer::loadAndCleanSchema);
    }

    private static List<String> loadAndCleanSchema(SchemaCacheKey cacheKey) {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            List<String> cleanedDdls = new ArrayList<>();
            for (String location : cacheKey.locations) {
                Resource[] resources = resolver.getResources(location);
                if (resources.length == 0) {
                    throw new IllegalArgumentException("Schema resource not found: " + location);
                }
                for (Resource resource : resources) {
                    String ddl = readResource(resource);
                    String cleaned = cleanMySqlSyntax(ddl);
                    cleanedDdls.add(cleaned);
                    log.debug("[JustTest] Schema loaded and cleaned: {}", resource.getFilename());
                }
            }
            return Collections.unmodifiableList(cleanedDdls);
        } catch (Exception e) {
            throw new IllegalStateException("[JustTest] Failed to read and clean schema resources", e);
        }
    }

    /**
     * 清理 MySQL 特有语法，使 DDL 兼容 H2。
     *
     * <p>清洗前先把字符串、反引号标识符和注释换成占位符，正则只改占位符以外的文本，最后再还原。
     * 这只划定词法边界，不是 SQL 解析器。</p>
     */
    static String cleanMySqlSyntax(String ddl) {
        List<String> protectedSegments = new ArrayList<>();
        String masked = maskProtectedSegments(ddl, protectedSegments);
        String result = masked;
        result = ENGINE_PATTERN.matcher(result).replaceAll("");
        result = AUTO_INCREMENT_PATTERN.matcher(result).replaceAll("");
        result = CHARSET_PATTERN.matcher(result).replaceAll("");
        result = COLLATE_TABLE_PATTERN.matcher(result).replaceAll("");
        result = COLLATE_COLUMN_PATTERN.matcher(result).replaceAll("");
        result = COMMENT_TABLE_PATTERN.matcher(result).replaceAll("");
        result = COMMENT_COLUMN_PATTERN.matcher(result).replaceAll("");
        result = ROW_FORMAT_PATTERN.matcher(result).replaceAll("");
        result = UNSIGNED_PATTERN.matcher(result).replaceAll("");
        result = ON_UPDATE_PATTERN.matcher(result).replaceAll("");
        result = CHARACTER_SET_PATTERN.matcher(result).replaceAll("");
        result = DEFAULT_BIT_PATTERN.matcher(result).replaceAll("DEFAULT $1");
        return restoreProtectedSegments(result, protectedSegments);
    }

    /**
     * 单引号接受反斜杠转义和成对单引号。反引号标识符只接受双写转义。
     * 位字面量 b'0' / B'1' 留在代码段里，供 DEFAULT b'0' 改写；内容只有 0 和 1。
     */
    private static String maskProtectedSegments(String ddl, List<String> protectedSegments) {
        StringBuilder code = new StringBuilder(ddl.length());
        int index = 0;
        while (index < ddl.length()) {
            char current = ddl.charAt(index);
            if (current == '\'' || current == '"' || current == '`') {
                index = maskQuoted(ddl, index, current, code, protectedSegments);
            } else if (startsBlockComment(ddl, index)) {
                index = maskBare(ddl, index, endOfBlockComment(ddl, index), code, protectedSegments);
            } else if (startsLineComment(ddl, index)) {
                index = maskBare(ddl, index, endOfLineComment(ddl, index), code, protectedSegments);
            } else {
                code.append(current);
                index++;
            }
        }
        return code.toString();
    }

    private static int maskQuoted(String ddl, int index, char quote, StringBuilder code,
                                  List<String> protectedSegments) {
        int end = endOfQuoted(ddl, index, quote, quote != '`');
        if (end < 0) {
            return maskBare(ddl, index, ddl.length(), code, protectedSegments);
        }
        if (quote == '\'' && isBitLiteral(ddl, index, end)) {
            code.append(ddl, index, end);
            return end;
        }
        int id = protectedSegments.size();
        protectedSegments.add(ddl.substring(index + 1, end - 1));
        code.append(quote);
        appendSegmentMark(code, id);
        code.append(quote);
        return end;
    }

    private static int maskBare(String ddl, int start, int end, StringBuilder code,
                                List<String> protectedSegments) {
        int id = protectedSegments.size();
        protectedSegments.add(ddl.substring(start, end));
        appendSegmentMark(code, id);
        return end;
    }

    private static void appendSegmentMark(StringBuilder code, int id) {
        code.append(SEGMENT_MARK).append(id).append(SEGMENT_MARK_END);
    }

    private static String restoreProtectedSegments(String masked, List<String> protectedSegments) {
        StringBuilder result = new StringBuilder(masked.length());
        int index = 0;
        while (index < masked.length()) {
            if (masked.charAt(index) == SEGMENT_MARK) {
                int cursor = index + 1;
                int id = 0;
                boolean sawDigit = false;
                while (cursor < masked.length()) {
                    char current = masked.charAt(cursor);
                    if (current < '0' || current > '9') {
                        break;
                    }
                    sawDigit = true;
                    id = id * 10 + (current - '0');
                    cursor++;
                }
                if (sawDigit
                        && cursor < masked.length()
                        && masked.charAt(cursor) == SEGMENT_MARK_END
                        && id < protectedSegments.size()) {
                    result.append(protectedSegments.get(id));
                    index = cursor + 1;
                    continue;
                }
            }
            result.append(masked.charAt(index));
            index++;
        }
        return result.toString();
    }

    private static int endOfQuoted(String sql, int index, char quote, boolean backslashEscapes) {
        int cursor = index + 1;
        while (cursor < sql.length()) {
            char current = sql.charAt(cursor);
            if (backslashEscapes && current == '\\' && cursor + 1 < sql.length()) {
                cursor += 2;
                continue;
            }
            if (current == quote) {
                if (cursor + 1 < sql.length() && sql.charAt(cursor + 1) == quote) {
                    cursor += 2;
                    continue;
                }
                return cursor + 1;
            }
            cursor++;
        }
        return -1;
    }

    private static boolean isBitLiteral(String ddl, int quoteIndex, int end) {
        if (quoteIndex == 0 || end - quoteIndex < 3) {
            return false;
        }
        char prefix = ddl.charAt(quoteIndex - 1);
        if (prefix != 'b' && prefix != 'B') {
            return false;
        }
        if (quoteIndex > 1 && isIdentifierPart(ddl.charAt(quoteIndex - 2))) {
            return false;
        }
        for (int index = quoteIndex + 1; index < end - 1; index++) {
            char current = ddl.charAt(index);
            if (current != '0' && current != '1') {
                return false;
            }
        }
        return true;
    }

    private static boolean isIdentifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$';
    }

    private static boolean startsBlockComment(String sql, int index) {
        return sql.charAt(index) == '/'
                && index + 1 < sql.length()
                && sql.charAt(index + 1) == '*';
    }

    private static int endOfBlockComment(String sql, int index) {
        int cursor = index + 2;
        while (cursor + 1 < sql.length()) {
            if (sql.charAt(cursor) == '*' && sql.charAt(cursor + 1) == '/') {
                return cursor + 2;
            }
            cursor++;
        }
        return sql.length();
    }

    private static boolean startsLineComment(String sql, int index) {
        char current = sql.charAt(index);
        if (current == '#') {
            return true;
        }
        if (current != '-' || index + 1 >= sql.length() || sql.charAt(index + 1) != '-') {
            return false;
        }
        if (index + 2 >= sql.length()) {
            return true;
        }
        return Character.isWhitespace(sql.charAt(index + 2));
    }

    private static int endOfLineComment(String sql, int index) {
        int cursor = index;
        while (cursor < sql.length()) {
            char current = sql.charAt(cursor);
            cursor++;
            if (current == '\n') {
                break;
            }
            if (current == '\r') {
                if (cursor < sql.length() && sql.charAt(cursor) == '\n') {
                    cursor++;
                }
                break;
            }
        }
        return cursor;
    }

    static void executeCachedDdl(JdbcTemplate jdbcTemplate, List<String> cleanedDdls) {
        for (String cleanedDdl : cleanedDdls) {
            executeDdl(jdbcTemplate, cleanedDdl);
        }
    }

    private static void executeDdl(JdbcTemplate jdbcTemplate, String ddl) {
        ByteArrayResource resource = new ByteArrayResource(ddl.getBytes(StandardCharsets.UTF_8));
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(resource, StandardCharsets.UTF_8));
            return null;
        });
    }

    private static JustTestRoutingDataSource resolveRoutingDataSource(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.getDataSource() instanceof JustTestRoutingDataSource
                ? (JustTestRoutingDataSource) jdbcTemplate.getDataSource()
                : null;
    }

    private static String readResource(Resource resource) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    private static final class SchemaCacheKey {
        private final List<String> locations;

        private SchemaCacheKey(String... schemaLocations) {
            if (schemaLocations == null) {
                throw new IllegalArgumentException("schemaLocations must not be null");
            }
            this.locations = Collections.unmodifiableList(
                    Arrays.asList(schemaLocations.clone()));
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SchemaCacheKey)) {
                return false;
            }
            SchemaCacheKey that = (SchemaCacheKey) other;
            return locations.equals(that.locations);
        }

        @Override
        public int hashCode() {
            return locations.hashCode();
        }
    }
}

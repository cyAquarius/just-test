package com.just.test.smarttest.internal.datasource;

import com.just.test.smarttest.internal.h2.H2FunctionRegistrar;
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
 * 重复读取资源和清理语法。对 {@link SmartTestRoutingDataSource}，默认先从共享
 * template 数据库通过 H2 {@code SCRIPT SIMPLE} 克隆 schema，并缓存 SCRIPT 语句列表，
 * 后续 case 重放该缓存而不再对 template 执行 {@code SCRIPT}；克隆失败时回退到
 * 缓存 DDL 重放。设置 {@code smarttest.schema.clone=false} 可禁用克隆并始终使用
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
    private static final Pattern COLLATE_PATTERN = Pattern.compile("\\s*COLLATE\\s*=?\\s*\\w+", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMENT_TABLE_PATTERN = Pattern.compile("\\s*COMMENT\\s*=\\s*'[^']*'", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMENT_COLUMN_PATTERN = Pattern.compile("\\s+COMMENT\\s+'[^']*'", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROW_FORMAT_PATTERN = Pattern.compile("\\s*ROW_FORMAT\\s*=\\s*\\w+", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNSIGNED_PATTERN = Pattern.compile("\\s+UNSIGNED", Pattern.CASE_INSENSITIVE);
    private static final Pattern ON_UPDATE_PATTERN = Pattern.compile("\\s+ON\\s+UPDATE\\s+CURRENT_TIMESTAMP(\\(\\d*\\))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHARACTER_SET_PATTERN = Pattern.compile("\\s+CHARACTER\\s+SET\\s+\\w+", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEFAULT_BIT_PATTERN = Pattern.compile("DEFAULT\\s+b'([01]+)'", Pattern.CASE_INSENSITIVE);

    /**
     * 初始化当前 case 的 schema。每个 case 只会标记并初始化自己的数据库；
     * schema cache、template clone 或缓存 DDL fallback 负责避免重复读取和清理资源。
     *
     * @param jdbcTemplate  H2 JdbcTemplate
     * @param schemaLocations DDL 文件路径（支持 classpath 通配符）
     */
    public static void initialize(JdbcTemplate jdbcTemplate, String... schemaLocations) {
        SmartTestRoutingDataSource routingDataSource = resolveRoutingDataSource(jdbcTemplate);
        if (routingDataSource != null && !routingDataSource.beginSchemaInitialization()) {
            return;
        }
        String dbKey = routingDataSource == null ? "external" : routingDataSource.currentDbKey();
        log.debug("[SmartTest] Initializing schema for database [{}]", dbKey);

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
            throw new RuntimeException("[SmartTest] Failed to initialize schema", e);
        }
    }

    private static List<String> getCachedSchema(String... schemaLocations) {
        SchemaCacheKey cacheKey = new SchemaCacheKey(schemaLocations);
        List<String> cached = CLEANED_SCHEMA_CACHE.get(cacheKey);
        if (cached != null) {
            log.debug("[SmartTest] Using cached cleaned schema for {}", cacheKey.locations);
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
                    log.debug("[SmartTest] Schema loaded and cleaned: {}", resource.getFilename());
                }
            }
            return Collections.unmodifiableList(cleanedDdls);
        } catch (Exception e) {
            throw new IllegalStateException("[SmartTest] Failed to read and clean schema resources", e);
        }
    }

    /**
     * 清理 MySQL 特有语法，使 DDL 兼容 H2。
     */
    static String cleanMySqlSyntax(String ddl) {
        String result = ddl;
        result = ENGINE_PATTERN.matcher(result).replaceAll("");
        result = AUTO_INCREMENT_PATTERN.matcher(result).replaceAll("");
        result = CHARSET_PATTERN.matcher(result).replaceAll("");
        result = COLLATE_PATTERN.matcher(result).replaceAll("");
        result = COMMENT_TABLE_PATTERN.matcher(result).replaceAll("");
        result = COMMENT_COLUMN_PATTERN.matcher(result).replaceAll("");
        result = ROW_FORMAT_PATTERN.matcher(result).replaceAll("");
        result = UNSIGNED_PATTERN.matcher(result).replaceAll("");
        result = ON_UPDATE_PATTERN.matcher(result).replaceAll("");
        result = CHARACTER_SET_PATTERN.matcher(result).replaceAll("");
        result = DEFAULT_BIT_PATTERN.matcher(result).replaceAll("DEFAULT $1");
        return result;
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

    private static SmartTestRoutingDataSource resolveRoutingDataSource(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.getDataSource() instanceof SmartTestRoutingDataSource
                ? (SmartTestRoutingDataSource) jdbcTemplate.getDataSource()
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

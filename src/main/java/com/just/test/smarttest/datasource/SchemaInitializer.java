package com.just.test.smarttest.datasource;

import com.just.test.smarttest.h2.H2FunctionRegistrar;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Schema 初始化器。加载 DDL 文件并自动清理 MySQL 特有语法后在 H2 上执行建表。
 * 整个测试生命周期只执行一次。
 */
public class SchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);
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
     * 初始化 schema，只执行一次。
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
        log.info("[SmartTest] Initializing schema for database [{}]", dbKey);

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            for (String location : schemaLocations) {
                Resource[] resources = resolver.getResources(location);
                if (resources.length == 0) {
                    throw new IllegalArgumentException("Schema resource not found: " + location);
                }
                for (Resource resource : resources) {
                    String ddl = readResource(resource);
                    String cleaned = cleanMySqlSyntax(ddl);
                    executeDdl(jdbcTemplate, cleaned);
                    log.info("[SmartTest] Schema loaded: {}", resource.getFilename());
                }
            }
            H2FunctionRegistrar.register(jdbcTemplate);
        } catch (Exception e) {
            if (routingDataSource != null) {
                routingDataSource.schemaInitializationFailed();
            }
            throw new RuntimeException("[SmartTest] Failed to initialize schema", e);
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
}

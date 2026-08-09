package com.just.test.smarttest.lifecycle;

import com.just.test.smarttest.context.ContextCreationLock;
import com.just.test.smarttest.datasource.SchemaInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

/**
 * Smart Test 生命周期管理器。
 *
 * <p>职责：beforeTestClass 阶段初始化 Schema。
 * 数据驱动方法的 prepare / verify / clean 全部由 {@link SmartTestExtension} 负责。</p>
 */
public class SmartTestExecutionListener implements TestExecutionListener, Ordered {

    private static final Logger log = LoggerFactory.getLogger(SmartTestExecutionListener.class);
    private static final String SCHEMA_LOCATION = "classpath:sql/schema.sql";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void beforeTestClass(TestContext testContext) {
        synchronized (ContextCreationLock.LOCK) {
            JdbcTemplate jdbcTemplate = SmartTestExtension.resolveJdbcTemplate(testContext.getApplicationContext());
            if (jdbcTemplate != null) {
                SchemaInitializer.initialize(jdbcTemplate, SCHEMA_LOCATION);
            }
        }
    }
}

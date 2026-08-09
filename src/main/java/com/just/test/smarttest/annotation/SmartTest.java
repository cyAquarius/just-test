package com.just.test.smarttest.annotation;

import com.just.test.smarttest.config.SmartTestDataSourceConfig;
import com.just.test.smarttest.lifecycle.SmartTestExecutionListener;
import com.just.test.smarttest.lifecycle.SmartTestExtension;
import com.just.test.smarttest.mock.SmartMockTestExecutionListener;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith({SpringExtension.class, SmartTestExtension.class})
@ContextConfiguration(classes = SmartTestDataSourceConfig.class)
@ActiveProfiles("test")
@TestExecutionListeners(
        listeners = {SmartTestExecutionListener.class, SmartMockTestExecutionListener.class},
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
public @interface SmartTest {
}

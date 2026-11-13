package com.just.test.lifecycle.beforecase.parametercase;

import com.just.test.annotation.BeforeCase;
import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

/**
 * Surefire 不会执行本类。由 {@code ParameterizedBeforeCaseValidationTest} 通过 EngineTestKit 启动，
 * 断言父类上带参的 {@code @BeforeCase} 在 BeforeAll 失败。
 */
@JustTest
@ContextConfiguration(classes = ParameterizedBeforeCaseCases.EmptyConfiguration.class)
public class ParameterizedBeforeCaseCases extends ParameterizedBeforeCaseParent {

    @CaseSource
    void run(CaseContext context) {
        context.setResult("should-not-run");
    }

    @Configuration
    static class EmptyConfiguration {
    }
}

abstract class ParameterizedBeforeCaseParent implements JustTestLifecycle {

    @BeforeCase("unused")
    void setupWithParameter(String unexpected) {
    }
}

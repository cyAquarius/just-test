package com.just.test.smarttest.annotation;

import com.just.test.smarttest.lifecycle.SmartTestClassValidationExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTestContextBootstrapper;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 启用 SmartTest 的 Spring Boot 测试基础设施。测试方法使用 {@link CaseSource} 声明 YAML case invocation。
 *
 * <p>该注解定义测试类级执行契约：类内可执行测试方法只能使用 {@code @CaseSource}；
 * 普通 JUnit 测试方法必须放在未标注 {@code @SmartTest} 的独立测试类中。</p>
 *
 * <p>框架基础配置由 Spring Test 的 ContextCustomizer 注册，因此这里不声明配置类；
 * Spring Boot 会自动发现消费工程的专用测试启动配置。</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith({SmartTestClassValidationExtension.class, SpringExtension.class})
@BootstrapWith(SpringBootTestContextBootstrapper.class)
@ContextConfiguration
@ActiveProfiles("test")
public @interface SmartTest {
}

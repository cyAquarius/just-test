package com.just.test.smarttest.annotation;

import com.just.test.smarttest.internal.project.SmartTestProjectRegistrar;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 消费工程的 SmartTest 专用启动配置。标注在空的测试启动类上即可组合
 * {@link SpringBootConfiguration}、{@link EnableAutoConfiguration} 以及框架默认的
 * 组件扫描 / 可选 MyBatis 装配。
 *
 * <p>框架拥有默认 denylist（生产启动类、Web/API 边界、classpath 上的 Feign / 调度刻板类型）
 * 与 SmartTest DataSource 别名；项目只声明差异：{@code basePackages}、可选
 * {@code mapperPackages}、可选的 {@code dataSourceAliases} / {@code transactionManagerAliases}，
 * 以及需要叠加的 include/exclude。外部依赖 Mock 仍放在 Support 的
 * {@link SmartMock} 上，本注解不会自动 mock 业务 Client。</p>
 *
 * <pre>
 * &#64;SmartTestProject(
 *     basePackages = "com.example",
 *     mapperPackages = "com.example.mapper"
 * )
 * public class SmartTestApplication {
 * }
 * </pre>
 *
 * <p>已有手写 {@code @SpringBootConfiguration} 启动类可继续使用，不必迁移。</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(SmartTestProjectRegistrar.class)
public @interface SmartTestProject {

    /**
     * {@link #basePackages} 的别名。
     */
    @AliasFor("basePackages")
    String[] value() default {};

    /**
     * 要扫描的业务根包。必须显式声明，框架不会猜测。
     */
    @AliasFor("value")
    String[] basePackages() default {};

    /**
     * MyBatis Mapper 扫描包。非空且 classpath 存在 mybatis-spring 时，
     * 自动注册 MapperScan 以及绑定 SmartTest DataSource 的
     * {@code SqlSessionFactory} / {@code SqlSessionTemplate}。
     * 未声明时不会猜测扫描根；声明了但缺少 mybatis-spring 时 fail-fast。
     */
    String[] mapperPackages() default {};

    /**
     * 额外注册为 {@code smartTestDataSource} 别名的 Bean 名，供存量
     * {@code @Qualifier("masterDataSource")} 等非标准名称使用。
     * 空白项忽略、重复项去重；目标名已有 Bean 定义或别名时跳过且不覆盖。
     * 默认的 {@code dataSource} 别名始终尝试注册，不受本属性影响。
     */
    String[] dataSourceAliases() default {};

    /**
     * 额外注册为 {@code smartTestTransactionManager} 别名的 Bean 名，供存量
     * {@code @Qualifier} / {@code @Transactional("masterDataTransactionManager")} 使用。
     * 空白项忽略、重复项去重；目标名已有 Bean 定义或别名时跳过且不覆盖。
     * 默认的 {@code transactionManager} 别名始终尝试注册，不受本属性影响。
     */
    String[] transactionManagerAliases() default {};

    /**
     * 额外按类型排除的组件，叠加在默认 denylist 之上。
     */
    Class<?>[] excludeClasses() default {};

    /**
     * 额外的组件扫描排除过滤器，叠加在默认 denylist 之上，不替换框架默认值。
     */
    ComponentScan.Filter[] excludeFilters() default {};

    /**
     * 额外的组件扫描包含过滤器，叠加在默认 {@code @Component} 匹配之上。
     */
    ComponentScan.Filter[] includeFilters() default {};

    /**
     * 额外排除的自动配置，对应 {@link EnableAutoConfiguration#exclude()}。
     */
    @AliasFor(annotation = EnableAutoConfiguration.class, attribute = "exclude")
    Class<?>[] excludeAutoConfiguration() default {};
}

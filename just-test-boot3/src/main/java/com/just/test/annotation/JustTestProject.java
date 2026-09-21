package com.just.test.annotation;

import com.just.test.internal.project.JustTestProjectRegistrar;
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
 * 消费工程的 JustTest 专用启动配置。标注在空的测试启动类上即可组合
 * {@link SpringBootConfiguration}、{@link EnableAutoConfiguration} 以及框架默认的
 * 组件扫描 / 可选 MyBatis 装配。
 *
 * <p>框架拥有默认 denylist（生产启动类、Web/API 边界、classpath 上的 Feign / 调度刻板类型）
 * 以及 JustTest DataSource 别名；保留 {@code FeignAutoConfiguration} / {@code FeignContext}，
 * 但不默认关闭 OkHttp。项目只声明差异：{@code basePackages}、可选
 * {@code mapperPackages}、可选的 {@code dataSourceAliases} / {@code transactionManagerAliases}，
 * 以及需要叠加的 include/exclude。Redis / OSS / SDK 等外部依赖 Mock 仍放在 Support 的
 * {@link JustMock} 上。{@code autoMockFeignClients} 默认关闭；开启后只 mock 带
 * {@code @FeignClient} 的接口，不按 {@code *Client} 名字推断。</p>
 *
 * <pre>
 * &#64;JustTestProject(
 *     basePackages = "com.example",
 *     mapperPackages = "com.example.mapper"
 * )
 * public class JustTestApplication {
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
@Import(JustTestProjectRegistrar.class)
public @interface JustTestProject {

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
     * 自动注册 MapperScan 以及绑定 JustTest DataSource 的
     * {@code SqlSessionFactory} / {@code SqlSessionTemplate}。
     * 未声明时不会猜测扫描根；声明了但缺少 mybatis-spring 时 fail-fast。
     */
    String[] mapperPackages() default {};

    /**
     * 额外注册为 {@code justTestDataSource} 别名的 Bean 名，供存量
     * {@code @Qualifier("masterDataSource")} 等非标准名称使用。
     * 空白项忽略、重复项去重；目标名已有 Bean 定义或别名时跳过且不覆盖。
     * 默认的 {@code dataSource} 别名始终尝试注册，不受本属性影响。
     */
    String[] dataSourceAliases() default {};

    /**
     * 额外注册为 {@code justTestTransactionManager} 别名的 Bean 名，供存量
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
     * 是否自动为 {@code @FeignClient} 接口注册与 {@link JustMock} 相同的
     * 线程作用域 Mockito mock。默认 {@code false}。
     *
     * <p>开启后按注解存在发现接口（不按 {@code *Client} 类名推断），并替换已有
     * Feign 代理定义，从而避免真实远程调用 / LoadBalancer 缺失导致的启动失败。
     * 不会排除 {@code FeignAutoConfiguration} / {@code FeignContext}。
     * 显式 {@link JustMock} 与 {@link ThreadScopedMock} 优先于自动 mock。</p>
     */
    boolean autoMockFeignClients() default false;

    /**
     * 在 {@link #autoMockFeignClients()} 为 {@code true} 时，跳过自动 mock 的
     * {@code @FeignClient} 类型。未开启自动 mock 时本属性无效。
     */
    Class<?>[] autoMockFeignClientExcludes() default {};

    /**
     * 历史上用于让框架默认关闭 Feign OkHttp。现已无效果：JustTest 不再向
     * Environment 写入 {@code feign.okhttp.enabled} /
     * {@code spring.cloud.openfeign.okhttp.enabled}。传输层由消费工程自行选择。
     *
     * <p>若 OkHttp 注册了名为 {@code client} 的 Bean，与
     * {@code @Resource private XxxClient client} 按字段名冲突，请在项目中自行
     * 关闭 OkHttp，或改为 {@code @Resource(name = ...)} / 重命名字段。</p>
     *
     * @deprecated 框架不再把 OkHttp 当作默认开关；保留属性以免旧代码无法编译。
     */
    @Deprecated
    boolean enableFeignOkHttp() default false;

    /**
     * 额外排除的自动配置，对应 {@link EnableAutoConfiguration#exclude()}。
     */
    @AliasFor(annotation = EnableAutoConfiguration.class, attribute = "exclude")
    Class<?>[] excludeAutoConfiguration() default {};
}

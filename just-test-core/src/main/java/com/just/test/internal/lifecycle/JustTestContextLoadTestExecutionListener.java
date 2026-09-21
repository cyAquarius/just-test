package com.just.test.internal.lifecycle;

import com.just.test.annotation.JustTestMarker;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * 在测试类 {@code BeforeAll} 阶段预加载 ApplicationContext。
 *
 * <p>Spring Test 在 Boot 2（Framework 5.3）默认不缓存 Context 加载失败；YAML
 * {@code @CaseSource} 每个 invocation 都会再次 {@code getApplicationContext()}。
 * 启动期配置错误（例如 mock 与 {@code *AutoConfiguration} 类型冲突）若拖到
 * {@code BeforeEach} 才爆发，同一失败会按 case 反复 refresh 并刷屏。</p>
 *
 * <p>本 listener 挂在 {@code SpringExtension.beforeAll} → {@code beforeTestClass}
 * 上，让 JUnit 在类级失败并中止后续 case。</p>
 *
 * <p>不对消费方提供兼容承诺。通过 {@code META-INF/spring.factories} 注册为
 * Spring Test SPI，因此必须保持 public。</p>
 */
public final class JustTestContextLoadTestExecutionListener extends AbstractTestExecutionListener {

    @Override
    public void beforeTestClass(TestContext testContext) {
        Class<?> testClass = testContext.getTestClass();
        if (!AnnotatedElementUtils.hasAnnotation(testClass, JustTestMarker.class)) {
            return;
        }
        testContext.getApplicationContext();
    }
}

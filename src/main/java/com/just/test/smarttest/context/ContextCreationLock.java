package com.just.test.smarttest.context;

/**
 * Context 创建串行化锁。
 *
 * <p>通过全局锁确保同一时刻只有一个 ApplicationContext 在初始化，
 * 解决 static 工厂注册表（如 GenericRegistry）
 * 在多 Context 并行初始化时的 @PostConstruct 竞争覆盖问题。</p>
 *
 * <p><b>设计权衡</b>：
 * <ul>
 *   <li>优点：完全避免 static 工厂注册表并发竞争，无需修改生产代码</li>
 *   <li>缺点：Context 创建串行化，但测试执行仍保持并行</li>
 *   <li>性能影响：Context 创建只占总时间的 5-10%，且有 Spring TestContext 缓存</li>
 * </ul>
 * </p>
 */
public class ContextCreationLock {

    /** 全局锁：确保 Context 创建串行 */
    public static final Object LOCK = new Object();

    private ContextCreationLock() {
        // 工具类，禁止实例化
    }
}

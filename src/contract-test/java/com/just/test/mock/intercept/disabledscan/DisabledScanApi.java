package com.just.test.mock.intercept.disabledscan;

import org.junit.jupiter.api.Disabled;

/**
 * JUnit 查找 {@code @Disabled} 时会看到接口。实现它的测试类不会执行。
 */
@Disabled
public interface DisabledScanApi {
}

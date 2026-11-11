package com.just.test.mock.interceptmiss;

import com.just.test.annotation.JustTest;

/**
 * 与 {@link JustTestApplication} 同包，扫描能看见它，但它没有声明 {@code OrphanClock} 的 {@code @JustMock}。
 * 类名不以 Test 结尾，Surefire 不会直接执行。
 */
@JustTest
public class PlainMissCases {
}

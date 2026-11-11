package com.just.test.mock.interceptmerge.lane;

import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustTest;
import com.just.test.mock.interceptmerge.fixtures.Lane;

/**
 * 接口同时对应两条实现。只供启动类包树扫描，Surefire 不直接执行。
 */
@JustTest
public class LaneCases {

    @JustMock
    private Lane lane;
}

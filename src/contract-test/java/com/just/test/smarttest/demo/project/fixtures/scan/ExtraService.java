package com.just.test.smarttest.demo.project.fixtures.scan;

import org.springframework.stereotype.Service;

/** 覆盖测试中通过 {@code excludeClasses} 排除的服务。 */
@Service
public class ExtraService {

    public String extra() {
        return "extra";
    }
}

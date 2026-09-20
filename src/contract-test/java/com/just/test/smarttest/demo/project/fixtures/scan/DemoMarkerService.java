package com.just.test.smarttest.demo.project.fixtures.scan;

import org.springframework.stereotype.Service;

/** 应被默认组件扫描纳入的业务协作类。 */
@Service
public class DemoMarkerService {

    public String marker() {
        return "scanned";
    }
}

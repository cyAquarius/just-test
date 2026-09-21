package com.just.test.demo.project.fixtures.feign;

import org.springframework.stereotype.Service;

@Service
public class DemoFeignGateway {

    private final DemoFeignClient demoFeignClient;

    public DemoFeignGateway(DemoFeignClient demoFeignClient) {
        this.demoFeignClient = demoFeignClient;
    }

    public String ping() {
        return demoFeignClient.ping();
    }
}

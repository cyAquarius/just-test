package com.just.test.demo.project.fixtures.feign;

import org.springframework.stereotype.Service;

@Service
public class OtherFeignGateway {

    private final OtherFeignClient otherFeignClient;

    public OtherFeignGateway(OtherFeignClient otherFeignClient) {
        this.otherFeignClient = otherFeignClient;
    }

    public String ping() {
        return otherFeignClient.ping();
    }
}

package com.just.test.demo.project.fixtures.feign;

import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "demo-feign")
public interface DemoFeignClient {

    String ping();
}

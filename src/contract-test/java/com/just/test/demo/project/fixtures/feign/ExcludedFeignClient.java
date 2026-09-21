package com.just.test.demo.project.fixtures.feign;

import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "excluded-feign")
public interface ExcludedFeignClient {

    String ping();
}

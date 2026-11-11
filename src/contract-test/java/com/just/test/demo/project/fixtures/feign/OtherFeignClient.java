package com.just.test.demo.project.fixtures.feign;

import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "other-feign")
public interface OtherFeignClient {

    String ping();
}

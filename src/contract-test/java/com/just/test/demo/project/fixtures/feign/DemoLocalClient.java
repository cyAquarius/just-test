package com.just.test.demo.project.fixtures.feign;

import org.springframework.stereotype.Component;

/** {@code *Client} 但没有 {@code @FeignClient}，不得被自动 mock。 */
@Component
public class DemoLocalClient {

    public String id() {
        return "real-local-client";
    }
}

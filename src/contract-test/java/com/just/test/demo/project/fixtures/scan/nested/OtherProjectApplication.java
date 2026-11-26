package com.just.test.demo.project.fixtures.scan.nested;

import com.just.test.annotation.JustTestProject;
import org.springframework.context.annotation.Bean;

@JustTestProject(basePackages = "com.just.test.demo.project.fixtures.scan.nested", autoMockFeignClients = true)
public class OtherProjectApplication {
    @Bean
    String otherProjectMarker() {
        return "unexpected";
    }
}

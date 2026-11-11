package com.just.test.mock.interceptmerge.fixtures;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LaneConfiguration {

    @Bean
    public EastLane eastLane() {
        return new EastLane();
    }

    @Bean
    public WestLane westLane() {
        return new WestLane();
    }
}

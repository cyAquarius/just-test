package com.just.test.mock.interceptmiss.fixtures;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrphanClockConfiguration {

    @Bean
    public OrphanClock orphanClock() {
        return new OrphanClock();
    }
}

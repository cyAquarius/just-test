package com.just.test.mock.interceptfinal.fixtures;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FinalClockConfiguration {

    @Bean
    public FinalClock finalClock() {
        return new FinalClock();
    }
}

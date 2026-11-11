package com.just.test.mock.interceptbean.fixtures;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TaskConfiguration {

    @Bean
    public TaskServiceImpl taskServiceImpl() {
        return new TaskServiceImpl();
    }

    @Bean
    public TaskReader taskReader(TaskService taskService) {
        return new TaskReader(taskService);
    }
}

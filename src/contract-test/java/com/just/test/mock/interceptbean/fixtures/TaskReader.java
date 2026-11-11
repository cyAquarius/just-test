package com.just.test.mock.interceptbean.fixtures;

public class TaskReader {

    private final TaskService taskService;

    public TaskReader(TaskService taskService) {
        this.taskService = taskService;
    }

    public String code() {
        return taskService.code();
    }

    public String label() {
        return taskService.label();
    }
}

package com.just.test.mock.interceptbean.fixtures;

public class TaskServiceImpl implements TaskService {

    @Override
    public String code() {
        return "live-code";
    }

    @Override
    public String label() {
        return this.code();
    }
}

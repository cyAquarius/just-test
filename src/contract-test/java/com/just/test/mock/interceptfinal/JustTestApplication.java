package com.just.test.mock.interceptfinal;

import com.just.test.annotation.JustTestProject;
import com.just.test.mock.interceptfinal.fixtures.FinalClock;

@JustTestProject(
        basePackages = "com.just.test.mock.interceptfinal.fixtures",
        interceptMocks = FinalClock.class
)
public class JustTestApplication {
}

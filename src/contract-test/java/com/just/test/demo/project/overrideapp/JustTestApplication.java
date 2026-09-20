package com.just.test.demo.project.overrideapp;

import com.just.test.annotation.JustTestProject;
import com.just.test.demo.project.fixtures.scan.ExtraService;
import com.just.test.demo.project.fixtures.scan.PlainCollaborator;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/** 在框架默认 denylist 上叠加项目差异：excludeFilters 排除 ExtraService、includeFilters 纳入无刻板类型。 */
@JustTestProject(
        basePackages = "com.just.test.demo.project.fixtures.scan",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = ExtraService.class
        ),
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = PlainCollaborator.class
        )
)
public class JustTestApplication {
}

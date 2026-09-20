package com.just.test.demo.project.fixtures.persist.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 由 {@code @JustTestProject(mapperPackages=...)} 注册的演示 Mapper。 */
public interface ProjectRecordMapper {

    @Insert("INSERT INTO parallel_case_record(id, marker) VALUES (1, #{marker})")
    void insert(@Param("marker") String marker);

    @Select("SELECT marker FROM parallel_case_record WHERE id = 1")
    String findMarker();
}

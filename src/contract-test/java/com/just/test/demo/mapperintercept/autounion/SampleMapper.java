package com.just.test.demo.mapperintercept.autounion;

import org.apache.ibatis.annotations.Select;

public interface SampleMapper {
    @Select("SELECT 'real-mapper'")
    String value();
}

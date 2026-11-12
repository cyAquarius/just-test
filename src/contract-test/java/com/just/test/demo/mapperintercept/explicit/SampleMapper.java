package com.just.test.demo.mapperintercept.explicit;

import org.apache.ibatis.annotations.Select;

public interface SampleMapper {
    @Select("SELECT 'real-mapper'")
    String value();
}

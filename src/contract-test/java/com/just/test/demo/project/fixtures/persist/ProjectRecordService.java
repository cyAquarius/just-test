package com.just.test.demo.project.fixtures.persist;

import com.just.test.demo.project.fixtures.persist.mapper.ProjectRecordMapper;
import org.springframework.stereotype.Service;

/** 注入框架装配的 Mapper，验证其绑定 JustTest H2。 */
@Service
public class ProjectRecordService {

    private final ProjectRecordMapper recordMapper;

    public ProjectRecordService(ProjectRecordMapper recordMapper) {
        this.recordMapper = recordMapper;
    }

    public String insertAndFind(String marker) {
        recordMapper.insert(marker);
        return recordMapper.findMarker();
    }
}

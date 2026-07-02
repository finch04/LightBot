package com.lightbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lightbot.entity.WorkflowTestRun;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作流测试运行记录 Mapper
 */
@Mapper
public interface WorkflowTestRunMapper extends BaseMapper<WorkflowTestRun> {
}

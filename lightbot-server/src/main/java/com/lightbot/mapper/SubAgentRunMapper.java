package com.lightbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lightbot.entity.SubAgentRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * SubAgentRun Mapper
 *
 * @author finch
 * @since 2026-06-25
 */
@Mapper
public interface SubAgentRunMapper extends BaseMapper<SubAgentRun> {

    @Select("SELECT * FROM subagent_run WHERE request_id = #{requestId}")
    SubAgentRun selectByRequestId(String requestId);
}

package com.lightbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lightbot.entity.SubAgent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * SubAgent Mapper
 *
 * @author finch
 * @since 2026-05-24
 */
@Mapper
public interface SubAgentMapper extends BaseMapper<SubAgent> {

    @Select("SELECT * FROM subagent WHERE name = #{name} AND deleted = 0")
    SubAgent selectByName(String name);
}
package com.lightbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lightbot.entity.AgentKnowledge;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent-知识库关联 Mapper
 *
 * @author finch
 * @since 2026-05-19
 */
@Mapper
public interface AgentKnowledgeMapper extends BaseMapper<AgentKnowledge> {
}

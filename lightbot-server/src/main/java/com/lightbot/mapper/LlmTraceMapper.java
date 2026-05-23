package com.lightbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lightbot.entity.LlmTrace;
import org.apache.ibatis.annotations.Mapper;

/**
 * LLM调用链追踪 Mapper
 *
 * @author finch
 * @since 2026-05-23
 */
@Mapper
public interface LlmTraceMapper extends BaseMapper<LlmTrace> {
}

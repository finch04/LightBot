package com.lightbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lightbot.entity.ToolCall;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工具调用记录 Mapper
 *
 * @author finch
 * @since 2026-05-22
 */
@Mapper
public interface ToolCallMapper extends BaseMapper<ToolCall> {
}

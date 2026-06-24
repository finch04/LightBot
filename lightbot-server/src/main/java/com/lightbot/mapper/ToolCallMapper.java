package com.lightbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lightbot.entity.ToolCall;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 工具调用记录 Mapper
 *
 * @author finch
 * @since 2026-05-22
 */
@Mapper
public interface ToolCallMapper extends BaseMapper<ToolCall> {

    /**
     * 批量删除指定消息关联的工具调用记录
     *
     * @param messageIds 消息ID列表
     * @return 删除行数
     */
    @Delete("<script>DELETE FROM tool_call WHERE message_id IN <foreach collection='messageIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int deleteByMessageIds(@Param("messageIds") List<Long> messageIds);
}

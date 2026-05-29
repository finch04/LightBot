package com.lightbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.entity.ToolCall;

import java.util.Map;

/**
 * 工具调用记录 Service
 *
 * @author finch
 * @since 2026-05-29
 */
public interface ToolCallService extends IService<ToolCall> {

    /**
     * 分页查询工具调用记录
     *
     * @param pageNum    页码
     * @param pageSize   每页大小
     * @param toolName   工具名称（可选）
     * @param status     状态（可选）
     * @param sessionId  会话ID（可选）
     * @param startTime  开始时间（可选）
     * @param endTime    结束时间（可选）
     * @return 分页结果
     */
    Map<String, Object> pageList(int pageNum, int pageSize, String toolName,
                                  String status, Long sessionId, String startTime, String endTime);

    /**
     * 记录工具调用（异步）
     *
     * @param toolCall 工具调用记录
     */
    void recordToolCall(ToolCall toolCall);
}

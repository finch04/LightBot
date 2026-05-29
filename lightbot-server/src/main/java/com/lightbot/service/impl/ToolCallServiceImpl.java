package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.entity.ToolCall;
import com.lightbot.mapper.ToolCallMapper;
import com.lightbot.service.ToolCallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具调用记录 Service 实现
 *
 * @author finch
 * @since 2026-05-29
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolCallServiceImpl extends ServiceImpl<ToolCallMapper, ToolCall>
        implements ToolCallService {

    @Override
    public Map<String, Object> pageList(int pageNum, int pageSize, String toolName,
                                         String status, Long sessionId, String startTime, String endTime) {
        // 1. 构建查询条件
        LambdaQueryWrapper<ToolCall> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(toolName)) {
            wrapper.like(ToolCall::getToolName, toolName);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(ToolCall::getStatus, status);
        }
        if (sessionId != null) {
            // 通过 message_id 关联查询（需要 message 表有 session_id）
            // 这里简化为直接按 message_id 查询
            wrapper.eq(ToolCall::getMessageId, sessionId);
        }
        if (StringUtils.hasText(startTime)) {
            wrapper.ge(ToolCall::getCreatedAt, startTime);
        }
        if (StringUtils.hasText(endTime)) {
            wrapper.le(ToolCall::getCreatedAt, endTime);
        }
        wrapper.orderByDesc(ToolCall::getCreatedAt);

        // 2. 分页查询
        Page<ToolCall> page = new Page<>(pageNum, pageSize);
        page = baseMapper.selectPage(page, wrapper);

        // 3. 封装返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("records", page.getRecords());
        result.put("total", page.getTotal());
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        return result;
    }

    @Async
    @Override
    public void recordToolCall(ToolCall toolCall) {
        try {
            if (toolCall.getCreatedAt() == null) {
                toolCall.setCreatedAt(java.time.LocalDateTime.now());
            }
            save(toolCall);
            log.debug("[工具调用记录] toolName=[{}], status=[{}]", toolCall.getToolName(), toolCall.getStatus());
        } catch (Exception e) {
            log.error("[工具调用记录] 写入失败, toolName={}", toolCall.getToolName(), e);
        }
    }
}

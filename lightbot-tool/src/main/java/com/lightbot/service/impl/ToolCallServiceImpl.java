package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.ToolCall;
import com.lightbot.mapper.ToolCallMapper;
import com.lightbot.service.ToolCallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    private final ObjectMapper objectMapper;

    /** tool_input 不合法 JSON 时保留的原始片段长度上限，避免占位 JSON 反而过大 */
    private static final int INVALID_JSON_PREVIEW_MAX = 200;

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
            // tool_input 是 JSONB 列，PG 要求严格合法 JSON。
            // LLM 输出可能因 maxTokens 在字符串中途截断，导致 args 不是完整 JSON；
            // 工具执行路径会调 tryRepairTruncatedWriteArgs 修复，但写入记录的仍是原始 args，
            // 此处兜底校验：不合法则压缩成短摘要 JSON 占位，避免写库失败丢整条调用记录
            toolCall.setToolInput(sanitizeToolInputForJsonb(toolCall.getToolInput()));
            save(toolCall);
            log.debug("[工具调用记录] toolName=[{}], status=[{}]", toolCall.getToolName(), toolCall.getStatus());
        } catch (Exception e) {
            log.error("[工具调用记录] 写入失败, toolName={}", toolCall.getToolName(), e);
        }
    }

    /**
     * 流式收尾线程（Reactor/HttpClient worker）上勿用 {@code saveBatch}：
     * MyBatis-Plus batch SPI（CompatibleHelper）在该线程 ClassLoader 下会抛 NoSuchElementException，
     * 导致整段 buildDoneEvent 失败、DONE 元数据丢失。改为逐条 insert。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void persistPendingCalls(Collection<ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return;
        }
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        for (ToolCall toolCall : toolCalls) {
            if (toolCall == null) {
                continue;
            }
            if (toolCall.getCreatedAt() == null) {
                toolCall.setCreatedAt(now);
            }
            toolCall.setToolInput(sanitizeToolInputForJsonb(toolCall.getToolInput()));
            save(toolCall);
        }
    }

    /**
     * 校验 toolInput 是否为合法 JSON，不合法则降级为占位 JSON
     * <p>占位保留 _parseError 标记和 _rawPreview 摘要，便于排查 LLM 输出截断问题</p>
     *
     * @param toolInput 原始工具参数字符串
     * @return 合法 JSON 则原样返回；不合法则返回占位 JSON；空值返回 "{}"
     */
    private String sanitizeToolInputForJsonb(String toolInput) {
        if (toolInput == null || toolInput.isBlank()) {
            return "{}";
        }
        try {
            objectMapper.readTree(toolInput);
            return toolInput;
        } catch (Exception parseErr) {
            // 截断的 JSON：保留前 200 字符便于排查，超出部分用 ... 标记
            String preview = toolInput.length() > INVALID_JSON_PREVIEW_MAX
                    ? toolInput.substring(0, INVALID_JSON_PREVIEW_MAX) + "...(truncated)"
                    : toolInput;
            Map<String, String> placeholder = new LinkedHashMap<>();
            placeholder.put("_parseError", "原始参数非合法 JSON（可能被 maxTokens 截断），已压缩保存");
            placeholder.put("_rawPreview", preview);
            try {
                String json = objectMapper.writeValueAsString(placeholder);
                log.warn("[工具调用记录] toolInput 非合法 JSON，降级为占位: parseErr={}, rawLen={}",
                        parseErr.getMessage(), toolInput.length());
                return json;
            } catch (Exception serializeErr) {
                // objectMapper 序列化失败极罕见，返回固定占位
                return "{\"_parseError\":\"原始参数非合法 JSON\"}";
            }
        }
    }
}

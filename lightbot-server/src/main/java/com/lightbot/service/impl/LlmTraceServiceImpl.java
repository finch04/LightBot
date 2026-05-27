package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.LlmTraceDetailVO;
import com.lightbot.dto.LlmTraceRequest;
import com.lightbot.dto.LlmTraceSpan;
import com.lightbot.entity.LlmTrace;
import com.lightbot.enums.ErrorCode;
import com.lightbot.mapper.LlmTraceMapper;
import com.lightbot.service.LlmTraceService;
import com.lightbot.util.LlmTraceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM调用链追踪 Service实现
 *
 * @author finch
 * @since 2026-05-23
 */
@Slf4j
@Service
public class LlmTraceServiceImpl extends ServiceImpl<LlmTraceMapper, LlmTrace>
        implements LlmTraceService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 分页查询调用链列表
     */
    @Override
    public Map<String, Object> pageList(LlmTraceRequest request) {
        // 1. 构建查询条件
        LambdaQueryWrapper<LlmTrace> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(request.getStatus()), LlmTrace::getStatus, request.getStatus())
                .eq(request.getSessionId() != null, LlmTrace::getSessionId, request.getSessionId())
                .eq(request.getAgentId() != null, LlmTrace::getAgentId, request.getAgentId());

        // 时间范围过滤
        if (StringUtils.hasText(request.getStartTime())) {
            wrapper.ge(LlmTrace::getCreateTime, LocalDateTime.parse(request.getStartTime(), FORMATTER));
        }
        if (StringUtils.hasText(request.getEndTime())) {
            wrapper.le(LlmTrace::getCreateTime, LocalDateTime.parse(request.getEndTime(), FORMATTER));
        }

        // 仅展示用户对话 trace（排除辅助能力：生成提示词、向量化、TTS 等）
        wrapper.and(w -> w.eq(LlmTrace::getTraceSource, "chat").or().isNull(LlmTrace::getTraceSource));

        wrapper.orderByDesc(LlmTrace::getCreateTime);

        // 2. 分页查询
        Page<LlmTrace> page = page(
                new Page<>(request.getPageNum(), request.getPageSize()),
                wrapper
        );

        // 3. 组装返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("records", page.getRecords());
        result.put("total", page.getTotal());
        result.put("pageNum", request.getPageNum());
        result.put("pageSize", request.getPageSize());
        return result;
    }

    /**
     * 查询调用链详情（spans JSON解析为对象列表）
     */
    @Override
    public LlmTraceDetailVO getDetail(Long id) {
        LlmTrace trace = getById(id);
        if (trace == null) {
            throw new BizException(ErrorCode.LLM_TRACE_NOT_FOUND);
        }

        // 转换为VO
        LlmTraceDetailVO vo = new LlmTraceDetailVO();
        BeanUtils.copyProperties(trace, vo);

        // 解析spans JSON字符串为对象列表
        if (trace.getSpans() != null && !trace.getSpans().isBlank()) {
            try {
                List<LlmTraceSpan> spans = OBJECT_MAPPER.readValue(trace.getSpans(), new TypeReference<>() {});
                vo.setSpans(spans);
            } catch (Exception e) {
                log.warn("[LLMTrace] spans解析失败, id={}, error={}", id, e.getMessage());
                vo.setSpans(List.of());
            }
        } else {
            vo.setSpans(List.of());
        }
        return vo;
    }

    /**
     * 汇总统计
     */
    @Override
    public Map<String, Object> getOverview() {
        LambdaQueryWrapper<LlmTrace> chatOnly = chatTraceWrapper();

        // 1. 总请求数（仅对话）
        long totalCount = count(chatOnly);

        // 2. 成功/失败数
        long successCount = count(chatOnly.clone().eq(LlmTrace::getStatus, "completed"));
        long failedCount = count(chatOnly.clone().eq(LlmTrace::getStatus, "failed"));

        // 3. 总Token数、平均耗时 — 通过SQL聚合
        Map<String, Object> aggregate = list(chatOnly.clone()
                .select(LlmTrace::getTotalTokens, LlmTrace::getTotalDurationMs, LlmTrace::getToolCallCount))
                .stream()
                .reduce(new HashMap<>(), (acc, trace) -> {
                    acc.merge("totalTokens", trace.getTotalTokens() != null ? trace.getTotalTokens() : 0, (a, b) -> (int) a + (int) b);
                    acc.merge("totalDurationMs", trace.getTotalDurationMs() != null ? trace.getTotalDurationMs() : 0L, (a, b) -> (long) a + (long) b);
                    acc.merge("totalToolCalls", trace.getToolCallCount() != null ? trace.getToolCallCount() : 0, (a, b) -> (int) a + (int) b);
                    return acc;
                }, (a, b) -> a);

        Map<String, Object> result = new HashMap<>();
        result.put("totalCount", totalCount);
        result.put("successCount", successCount);
        result.put("failedCount", failedCount);
        result.put("totalTokens", aggregate.getOrDefault("totalTokens", 0));
        result.put("avgDurationMs", totalCount > 0 ? (long) aggregate.getOrDefault("totalDurationMs", 0L) / totalCount : 0);
        result.put("totalToolCalls", aggregate.getOrDefault("totalToolCalls", 0));
        return result;
    }

    /**
     * 异步写入调用链记录
     */
    private LambdaQueryWrapper<LlmTrace> chatTraceWrapper() {
        return new LambdaQueryWrapper<LlmTrace>()
                .and(w -> w.eq(LlmTrace::getTraceSource, "chat").or().isNull(LlmTrace::getTraceSource));
    }

    @Async
    @Override
    public void recordTrace(LlmTrace trace) {
        if (LlmTraceContext.isSuppressed()) {
            log.debug("[LLMTrace] 跳过辅助能力 trace: requestId={}", trace.getRequestId());
            return;
        }
        if (trace.getTraceSource() == null) {
            trace.setTraceSource("chat");
        }
        if (!"chat".equals(trace.getTraceSource())) {
            return;
        }
        try {
            save(trace);
        } catch (Exception e) {
            log.error("[LLMTrace] 异步写入调用链失败, requestId={}", trace.getRequestId(), e);
        }
    }

    /**
     * 删除会话下的所有调用链记录
     */
    @Override
    public void deleteBySessionId(Long sessionId) {
        remove(new LambdaQueryWrapper<LlmTrace>().eq(LlmTrace::getSessionId, sessionId));
    }
}

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
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class LlmTraceServiceImpl extends ServiceImpl<LlmTraceMapper, LlmTrace>
        implements LlmTraceService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ObjectMapper objectMapper;

    /**
     * 分页查询调用链列表
     */
    @Override
    public Map<String, Object> pageList(LlmTraceRequest request) {
        // 1. 构建查询条件
        LambdaQueryWrapper<LlmTrace> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(request.getStatus()), LlmTrace::getStatus, request.getStatus())
                .eq(request.getSessionId() != null, LlmTrace::getSessionId, request.getSessionId())
                .eq(StringUtils.hasText(request.getRequestId()), LlmTrace::getRequestId, request.getRequestId())
                .eq(request.getAgentId() != null, LlmTrace::getAgentId, request.getAgentId());

        // 时间范围过滤
        if (StringUtils.hasText(request.getStartTime())) {
            wrapper.ge(LlmTrace::getCreateTime, LocalDateTime.parse(request.getStartTime(), FORMATTER));
        }
        if (StringUtils.hasText(request.getEndTime())) {
            wrapper.le(LlmTrace::getCreateTime, LocalDateTime.parse(request.getEndTime(), FORMATTER));
        }

        // 按来源类型筛选：指定 traceSource 时精确匹配，否则展示 chat + workflow（排除辅助能力）
        if (StringUtils.hasText(request.getTraceSource())) {
            wrapper.eq(LlmTrace::getTraceSource, request.getTraceSource());
        } else {
            wrapper.and(w -> w.eq(LlmTrace::getTraceSource, "chat")
                    .or().eq(LlmTrace::getTraceSource, "workflow")
                    .or().isNull(LlmTrace::getTraceSource));
        }

        wrapper.orderByDesc(LlmTrace::getCreateTime);

        // 2. 分页查询
        Page<LlmTrace> page = baseMapper.selectPage(
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
                List<LlmTraceSpan> spans = objectMapper.readValue(trace.getSpans(), new TypeReference<>() {});
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
     * 汇总统计（SQL 聚合，避免全量加载到内存）
     */
    @Override
    public Map<String, Object> getOverview(String traceSource) {
        // 1. SQL 聚合：total_count / total_tokens / avg_duration_ms / total_tool_calls
        String source = StringUtils.hasText(traceSource) ? traceSource : null;
        Map<String, Object> aggregate = baseMapper.aggregateOverview(source);

        long totalCount = ((Number) aggregate.getOrDefault("total_count", 0)).longValue();

        // 2. 成功/失败数（有索引，count 很快）
        LambdaQueryWrapper<LlmTrace> base = chatTraceWrapper(traceSource);
        long successCount = count(base.clone().eq(LlmTrace::getStatus, "completed"));
        long failedCount = count(base.clone().eq(LlmTrace::getStatus, "failed"));

        // 3. 组装返回
        Map<String, Object> result = new HashMap<>();
        result.put("totalCount", totalCount);
        result.put("successCount", successCount);
        result.put("failedCount", failedCount);
        result.put("totalTokens", aggregate.getOrDefault("total_tokens", 0));
        result.put("avgDurationMs", totalCount > 0
                ? ((Number) aggregate.getOrDefault("avg_duration_ms", 0)).longValue() : 0);
        result.put("totalToolCalls", aggregate.getOrDefault("total_tool_calls", 0));
        return result;
    }

    /**
     * 构建 trace 来源筛选条件
     */
    private LambdaQueryWrapper<LlmTrace> chatTraceWrapper(String traceSource) {
        if (StringUtils.hasText(traceSource)) {
            return new LambdaQueryWrapper<LlmTrace>().eq(LlmTrace::getTraceSource, traceSource);
        }
        return new LambdaQueryWrapper<LlmTrace>()
                .and(w -> w.eq(LlmTrace::getTraceSource, "chat")
                        .or().eq(LlmTrace::getTraceSource, "workflow")
                        .or().isNull(LlmTrace::getTraceSource));
    }

    @Async("lightBotExecutor")
    @Override
    public void recordTrace(LlmTrace trace) {
        if (LlmTraceContext.isSuppressed()) {
            log.debug("[LLMTrace] 跳过辅助能力 trace: requestId={}", trace.getRequestId());
            return;
        }
        if (trace.getTraceSource() == null) {
            trace.setTraceSource("chat");
        }
        // 仅允许 chat 和 workflow 来源写入
        if (!"chat".equals(trace.getTraceSource()) && !"workflow".equals(trace.getTraceSource())) {
            return;
        }
        try {
            trace.setReplyContent(com.lightbot.util.TextNormalizeUtil.sanitizeForDatabase(trace.getReplyContent()));
            trace.setErrorMessage(com.lightbot.util.TextNormalizeUtil.sanitizeForDatabase(trace.getErrorMessage()));
            trace.setSpans(com.lightbot.util.TextNormalizeUtil.sanitizeForDatabase(trace.getSpans()));
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

    /**
     * 批量删除调用链记录（物理删除）
     */
    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        removeByIds(ids);
    }
}

package com.lightbot.controller;

import com.lightbot.common.Result;
import com.lightbot.dto.LlmTraceDetailVO;
import com.lightbot.dto.LlmTraceRequest;
import com.lightbot.service.LlmTraceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * LLM调用链追踪 Controller
 *
 * @author finch
 * @since 2026-05-23
 */
@Tag(name = "可观测性", description = "AI调用链追踪查询")
@RestController
@RequestMapping("/api/observability")
@RequiredArgsConstructor
public class LlmTraceController {

    private final LlmTraceService llmTraceService;

    /**
     * 分页查询调用链列表
     */
    @GetMapping("/traces")
    @Operation(summary = "调用链列表")
    public Result<Map<String, Object>> listTraces(LlmTraceRequest request) {
        return Result.ok(llmTraceService.pageList(request));
    }

    /**
     * 查询调用链详情（spans解析为对象列表）
     */
    @GetMapping("/traces/{id}")
    @Operation(summary = "调用链详情")
    public Result<LlmTraceDetailVO> getTraceDetail(@PathVariable Long id) {
        return Result.ok(llmTraceService.getDetail(id));
    }

    /**
     * 汇总统计
     */
    @GetMapping("/overview")
    @Operation(summary = "调用链汇总统计")
    public Result<Map<String, Object>> getOverview(@RequestParam(required = false) String traceSource) {
        return Result.ok(llmTraceService.getOverview(traceSource));
    }
}

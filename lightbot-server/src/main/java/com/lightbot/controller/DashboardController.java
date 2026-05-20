package com.lightbot.controller;

import com.lightbot.common.Result;
import com.lightbot.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Dashboard统计接口
 *
 * @author finch
 * @since 2026-05-20
 */
@Tag(name = "Dashboard", description = "平台统计概览")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "获取基础统计概览")
    @GetMapping("/basic")
    public Result<Map<String, Object>> getBasicStats() {
        return Result.ok(dashboardService.getBasicStats());
    }

    @Operation(summary = "获取Agent统计详情")
    @GetMapping("/agents")
    public Result<Map<String, Object>> getAgentStats() {
        return Result.ok(dashboardService.getAgentStats());
    }

    @Operation(summary = "获取知识库统计详情")
    @GetMapping("/knowledge")
    public Result<Map<String, Object>> getKnowledgeStats() {
        return Result.ok(dashboardService.getKnowledgeStats());
    }

    @Operation(summary = "获取对话统计详情")
    @GetMapping("/chat")
    public Result<Map<String, Object>> getChatStats() {
        return Result.ok(dashboardService.getChatStats());
    }
}

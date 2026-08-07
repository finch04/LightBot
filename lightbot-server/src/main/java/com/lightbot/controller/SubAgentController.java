package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.common.Result;
import com.lightbot.dto.SubAgentRequestDTO;
import com.lightbot.entity.SubAgent;
import com.lightbot.service.SubAgentService;
import com.lightbot.service.ChatSessionService;
import com.lightbot.service.ResearchTaskProjectionService;
import com.lightbot.subagent.service.SubAgentTaskService;
import com.lightbot.vo.ResearchTaskProjectionVO;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SubAgent 管理接口
 *
 * @author finch
 * @since 2026-05-24
 */
@Tag(name = "SubAgent管理", description = "子智能体的增删改查")
@RestController
@RequestMapping("/api/subagents")
@RequiredArgsConstructor
public class SubAgentController {

    private final SubAgentService subAgentService;
    private final ChatSessionService chatSessionService;
    private final SubAgentTaskService subAgentTaskService;
    private final ResearchTaskProjectionService researchTaskProjectionService;

    @Operation(summary = "新增SubAgent")
    @PostMapping
    public Result<SubAgent> create(@Valid @RequestBody SubAgentRequestDTO request) {
        return Result.ok(subAgentService.create(request));
    }

    @Operation(summary = "更新SubAgent")
    @PutMapping
    public Result<SubAgent> update(@Valid @RequestBody SubAgentRequestDTO request) {
        return Result.ok(subAgentService.update(request));
    }

    @Operation(summary = "删除SubAgent")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        subAgentService.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "分页查询SubAgent")
    @GetMapping
    public Result<Page<SubAgent>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "100") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isBuiltin) {
        return Result.ok(subAgentService.listPage(pageNum, pageSize, keyword, isBuiltin));
    }

    @Operation(summary = "获取单个SubAgent")
    @GetMapping("/{id}")
    public Result<SubAgent> getById(@PathVariable Long id) {
        return Result.ok(subAgentService.getById(id));
    }

    @Operation(summary = "获取所有启用的SubAgent")
    @GetMapping("/enabled")
    public Result<List<SubAgent>> listEnabled() {
        return Result.ok(subAgentService.listEnabled());
    }

    @Operation(summary = "设置SubAgent启用状态")
    @PutMapping("/{id}/enabled")
    public Result<Void> setEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        subAgentService.setEnabled(id, enabled);
        return Result.ok();
    }

    @Operation(summary = "分页查询会话内的SubAgent任务")
    @GetMapping("/runs")
    public Result<Page<com.lightbot.entity.SubAgentRun>> listRuns(
            @RequestParam Long sessionId,
            @RequestParam(required = false) String batchId,
            @RequestParam(required = false) String parentRequestId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        chatSessionService.ensurePlatformOwnedByUser(sessionId, StpUtil.getLoginIdAsLong());
        return Result.ok(subAgentTaskService.pageRuns(sessionId, batchId, parentRequestId, pageNum, pageSize));
    }

    @Operation(summary = "获取会话侧栏的SubAgent运行态摘要")
    @GetMapping("/runs/summary")
    public Result<List<java.util.Map<String, Object>>> listRunSummaries(
            @RequestParam Long sessionId,
            @RequestParam(required = false) String parentRequestId,
            @RequestParam(defaultValue = "20") int limit) {
        chatSessionService.ensurePlatformOwnedByUser(sessionId, StpUtil.getLoginIdAsLong());
        return Result.ok(subAgentTaskService.listRuntimeSummaries(sessionId, parentRequestId, limit));
    }

    @Operation(summary = "获取当前请求的调研协作状态投影")
    @GetMapping("/runs/projection")
    public Result<ResearchTaskProjectionVO> getResearchProjection(
            @RequestParam Long sessionId,
            @RequestParam String parentRequestId) {
        chatSessionService.ensurePlatformOwnedByUser(sessionId, StpUtil.getLoginIdAsLong());
        return Result.ok(researchTaskProjectionService.getProjection(sessionId, parentRequestId));
    }

    @Operation(summary = "获取SubAgent批次详情")
    @GetMapping("/batches/{batchId}")
    public Result<java.util.Map<String, Object>> getBatch(@PathVariable String batchId, @RequestParam Long sessionId) {
        chatSessionService.ensurePlatformOwnedByUser(sessionId, StpUtil.getLoginIdAsLong());
        return Result.ok(subAgentTaskService.getBatchDetail(batchId, sessionId));
    }

    @Operation(summary = "取消SubAgent批次")
    @PostMapping("/batches/{batchId}/cancel")
    public Result<java.util.Map<String, Object>> cancelBatch(@PathVariable String batchId, @RequestParam Long sessionId) {
        chatSessionService.ensurePlatformOwnedByUser(sessionId, StpUtil.getLoginIdAsLong());
        return Result.ok(subAgentTaskService.cancelBatch(batchId, sessionId));
    }

    @Operation(summary = "取消SubAgent单任务")
    @PostMapping("/runs/{taskId}/cancel")
    public Result<java.util.Map<String, Object>> cancelRun(@PathVariable String taskId, @RequestParam Long sessionId) {
        chatSessionService.ensurePlatformOwnedByUser(sessionId, StpUtil.getLoginIdAsLong());
        return Result.ok(subAgentTaskService.cancelTask(taskId, sessionId));
    }

    @Operation(summary = "获取SubAgent单任务详情")
    @GetMapping("/runs/{taskId}")
    public Result<java.util.Map<String, Object>> getRun(@PathVariable String taskId, @RequestParam Long sessionId) {
        chatSessionService.ensurePlatformOwnedByUser(sessionId, StpUtil.getLoginIdAsLong());
        return Result.ok(subAgentTaskService.getTaskDetail(taskId, sessionId));
    }

    @Operation(summary = "获取SubAgent任务的独立子线程详情")
    @GetMapping("/runs/{taskId}/thread")
    public Result<java.util.Map<String, Object>> getRunThread(@PathVariable String taskId, @RequestParam Long sessionId) {
        chatSessionService.ensurePlatformOwnedByUser(sessionId, StpUtil.getLoginIdAsLong());
        return Result.ok(subAgentTaskService.getTaskThreadDetail(taskId, sessionId));
    }

    @Operation(summary = "按游标获取SubAgent任务运行事件")
    @GetMapping("/runs/{taskId}/events")
    public Result<java.util.Map<String, Object>> getRunEvents(
            @PathVariable String taskId,
            @RequestParam Long sessionId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "50") int limit) {
        chatSessionService.ensurePlatformOwnedByUser(sessionId, StpUtil.getLoginIdAsLong());
        return Result.ok(subAgentTaskService.getTaskEvents(taskId, sessionId, cursor, limit));
    }
}

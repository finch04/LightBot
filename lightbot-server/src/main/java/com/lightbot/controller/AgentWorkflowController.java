package com.lightbot.controller;

import com.lightbot.common.Result;
import com.lightbot.dto.WorkflowGraphDTO;
import com.lightbot.dto.WorkflowNodeTestRequest;
import com.lightbot.dto.WorkflowResumeRequest;
import com.lightbot.dto.WorkflowTestRequest;
import com.lightbot.dto.WorkflowTestResultVO;
import com.lightbot.dto.WorkflowVersionVO;
import com.lightbot.service.WorkflowConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Agent 工作流配置接口
 */
@Tag(name = "Agent工作流", description = "工作流草稿、发布、版本与调试")
@RestController
@RequestMapping("/api/agents/{agentId}/workflow")
@RequiredArgsConstructor
public class AgentWorkflowController {

    private final WorkflowConfigService workflowConfigService;

    @Operation(summary = "获取工作流配置（草稿/发布状态）")
    @GetMapping
    public Result<Map<String, Object>> getConfig(@PathVariable Long agentId) {
        return Result.ok(workflowConfigService.getWorkflowConfig(agentId));
    }

    @Operation(summary = "暂存工作流草稿（跳过校验）")
    @PostMapping("/draft")
    public Result<Void> saveDraft(@PathVariable Long agentId, @RequestBody WorkflowGraphDTO graph) {
        workflowConfigService.saveDraft(agentId, graph);
        return Result.ok();
    }

    @Operation(summary = "发布工作流（需通过校验）")
    @PostMapping("/publish")
    public Result<Map<String, Object>> publish(@PathVariable Long agentId, @RequestBody WorkflowGraphDTO graph) {
        return Result.ok(workflowConfigService.publish(agentId, graph));
    }

    @Operation(summary = "校验工作流配置")
    @PostMapping("/validate")
    public Result<List<String>> validate(@PathVariable Long agentId, @RequestBody WorkflowGraphDTO graph) {
        return Result.ok(workflowConfigService.validate(agentId, graph));
    }

    @Operation(summary = "工作流版本列表")
    @GetMapping("/versions")
    public Result<List<WorkflowVersionVO>> listVersions(@PathVariable Long agentId) {
        return Result.ok(workflowConfigService.listVersions(agentId));
    }

    @Operation(summary = "获取历史版本画布配置")
    @GetMapping("/versions/{version}")
    public Result<Map<String, Object>> getVersionGraph(
            @PathVariable Long agentId,
            @PathVariable Integer version) {
        return Result.ok(workflowConfigService.getVersionGraph(agentId, version));
    }

    @Operation(summary = "恢复历史版本到草稿")
    @PostMapping("/versions/{version}/restore")
    public Result<Void> restoreVersion(@PathVariable Long agentId, @PathVariable Integer version) {
        workflowConfigService.restoreVersion(agentId, version);
        return Result.ok();
    }

    @Operation(summary = "调试运行工作流")
    @PostMapping("/test")
    public Result<WorkflowTestResultVO> testRun(
            @PathVariable Long agentId,
            @RequestBody @Valid WorkflowTestRequest request) {
        return Result.ok(workflowConfigService.testRun(agentId, request));
    }

    @Operation(summary = "人工确认后恢复工作流")
    @PostMapping("/resume")
    public Result<WorkflowTestResultVO> resume(
            @PathVariable Long agentId,
            @RequestBody @Valid WorkflowResumeRequest request) {
        return Result.ok(workflowConfigService.resumeWorkflow(agentId, request));
    }

    @Operation(summary = "单节点调试运行")
    @PostMapping("/test-node")
    public Result<WorkflowTestResultVO> testNode(
            @PathVariable Long agentId,
            @RequestBody @Valid WorkflowNodeTestRequest request) {
        return Result.ok(workflowConfigService.testNode(agentId, request));
    }
}

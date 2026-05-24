package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.common.BizException;
import com.lightbot.common.Result;
import com.lightbot.entity.Agent;
import com.lightbot.entity.McpServer;
import com.lightbot.entity.Tool;
import com.lightbot.enums.ErrorCode;
import com.lightbot.service.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Agent管理接口
 *
 * @author finch
 * @since 2026-05-19
 */
@Tag(name = "Agent管理", description = "Agent的增删改查")
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @Operation(summary = "创建Agent")
    @PostMapping
    public Result<Agent> create(@RequestBody Agent agent) {
        return Result.ok(agentService.create(agent));
    }

    @Operation(summary = "更新Agent")
    @PutMapping
    public Result<Agent> update(@RequestBody Agent agent) {
        return Result.ok(agentService.update(agent));
    }

    @Operation(summary = "分页查询当前用户的Agent列表")
    @GetMapping
    public Result<Page<Agent>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String name) {
        return Result.ok(agentService.listMyAgents(pageNum, pageSize, name));
    }

    @Operation(summary = "获取Agent详情（含绑定的知识库ID列表）")
    @GetMapping("/{id}/detail")
    public Result<Map<String, Object>> getDetail(@PathVariable Long id) {
        return Result.ok(agentService.getAgentDetail(id));
    }

    @Operation(summary = "获取Agent详情")
    @GetMapping("/{id}")
    public Result<Agent> getById(@PathVariable Long id) {
        return Result.ok(agentService.getById(id));
    }

    @Operation(summary = "更新Agent绑定的知识库")
    @PutMapping("/{id}/knowledge")
    public Result<Void> updateKnowledgeBindings(
            @PathVariable Long id,
            @RequestBody List<Long> knowledgeIds) {
        if (knowledgeIds != null && knowledgeIds.size() > 3) {
            throw new BizException(ErrorCode.AGENT_KNOWLEDGE_LIMIT);
        }
        agentService.updateKnowledgeBindings(id, knowledgeIds);
        return Result.ok();
    }

    @Operation(summary = "获取Agent绑定的知识库ID列表")
    @GetMapping("/{id}/knowledge")
    public Result<List<Long>> getKnowledgeIds(@PathVariable Long id) {
        return Result.ok(agentService.getKnowledgeIds(id));
    }

    @Operation(summary = "更新Agent绑定的工具")
    @PutMapping("/{id}/tools")
    public Result<Void> updateToolBindings(
            @PathVariable Long id,
            @RequestBody List<Long> toolIds) {
        agentService.updateToolBindings(id, toolIds);
        return Result.ok();
    }

    @Operation(summary = "获取Agent绑定的工具ID列表")
    @GetMapping("/{id}/tools")
    public Result<List<String>> getToolIds(@PathVariable Long id) {
        List<Long> toolIds = agentService.getToolIds(id);
        List<String> toolIdStrs = toolIds.stream().map(String::valueOf).toList();
        return Result.ok(toolIdStrs);
    }

    @Operation(summary = "获取Agent绑定的工具详情列表")
    @GetMapping("/{id}/tools/detail")
    public Result<List<Tool>> getToolDetails(@PathVariable Long id) {
        return Result.ok(agentService.getToolDetails(id));
    }

    @Operation(summary = "获取Agent绑定的MCP Server ID列表")
    @GetMapping("/{id}/mcp-servers")
    public Result<List<String>> getMcpServerIds(@PathVariable Long id) {
        List<Long> mcpServerIds = agentService.getMcpServerIds(id);
        List<String> mcpServerIdStrs = mcpServerIds.stream().map(String::valueOf).toList();
        return Result.ok(mcpServerIdStrs);
    }

    @Operation(summary = "更新Agent绑定的MCP Server")
    @PutMapping("/{id}/mcp-servers")
    public Result<Void> updateMcpServerBindings(
            @PathVariable Long id,
            @RequestBody List<Long> mcpServerIds) {
        agentService.updateMcpServerBindings(id, mcpServerIds);
        return Result.ok();
    }

    @Operation(summary = "获取Agent绑定的MCP Server详情列表")
    @GetMapping("/{id}/mcp-servers/detail")
    public Result<List<McpServer>> getMcpServerDetails(@PathVariable Long id) {
        return Result.ok(agentService.getMcpServerDetails(id));
    }

    @Operation(summary = "获取Agent绑定的SubAgent ID列表")
    @GetMapping("/{id}/subagents")
    public Result<List<String>> getSubAgentIds(@PathVariable Long id) {
        List<Long> subAgentIds = agentService.getSubAgentIds(id);
        List<String> subAgentIdStrs = subAgentIds.stream().map(String::valueOf).toList();
        return Result.ok(subAgentIdStrs);
    }

    @Operation(summary = "更新Agent绑定的SubAgent")
    @PutMapping("/{id}/subagents")
    public Result<Void> updateSubAgentBindings(
            @PathVariable Long id,
            @RequestBody List<Long> subAgentIds) {
        agentService.updateSubAgentBindings(id, subAgentIds);
        return Result.ok();
    }

    @Operation(summary = "删除Agent")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        agentService.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "AI生成系统提示词")
    @PostMapping("/{id}/generate-prompt")
    public Result<String> generatePrompt(@PathVariable Long id) {
        return Result.ok(agentService.generateSystemPrompt(id));
    }

    @Operation(summary = "AI生成推荐问题")
    @PostMapping("/{id}/generate-questions")
    public Result<String> generateQuestions(@PathVariable Long id) {
        return Result.ok(agentService.generateRecommendedQuestions(id));
    }

    @Operation(summary = "设置为默认Agent")
    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        agentService.setDefaultAgent(id);
        return Result.ok();
    }

    @Operation(summary = "上传Agent头像")
    @PostMapping("/{id}/avatar")
    public Result<String> uploadAvatar(@PathVariable Long id,
                                       @RequestParam("file") MultipartFile file) {
        return Result.ok(agentService.uploadAvatar(id, file));
    }
}

package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.common.BizException;
import com.lightbot.common.Result;
import com.lightbot.dto.AgentChatCapabilitiesDTO;
import com.lightbot.dto.AgentPublishRequest;
import com.lightbot.dto.WorkflowVersionVO;
import com.lightbot.dto.WorkflowExampleVO;
import com.lightbot.entity.Agent;
import com.lightbot.entity.McpServer;
import com.lightbot.entity.Tool;
import com.lightbot.enums.ErrorCode;
import com.lightbot.service.AgentService;
import com.lightbot.service.AgentVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
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
    private final AgentVersionService agentVersionService;

    @Operation(summary = "创建Agent")
    @PostMapping
    public Result<Agent> create(@RequestBody @Valid Agent agent) {
        return Result.ok(agentService.create(agent));
    }

    @Operation(summary = "更新Agent")
    @PutMapping
    public Result<Agent> update(@RequestBody @Valid Agent agent) {
        return Result.ok(agentService.update(agent));
    }

    @Operation(summary = "分页查询当前用户的Agent列表")
    @GetMapping
    public Result<Page<Agent>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String agentType,
            @RequestParam(defaultValue = "true") boolean includeDefault) {
        return Result.ok(agentService.listMyAgents(pageNum, pageSize, name, agentType, includeDefault));
    }

    @Operation(summary = "获取内置示例工作流列表")
    @GetMapping("/workflow-examples")
    public Result<List<WorkflowExampleVO>> listWorkflowExamples() {
        return Result.ok(agentService.listWorkflowExamples());
    }

    @Operation(summary = "根据示例创建工作流Agent")
    @PostMapping("/workflow-examples/{key}")
    public Result<Agent> createFromWorkflowExample(@PathVariable String key) {
        return Result.ok(agentService.createFromWorkflowExample(key));
    }

    @Operation(summary = "获取Agent详情（含绑定的知识库ID列表）")
    @GetMapping("/{id}/detail")
    public Result<Map<String, Object>> getDetail(@PathVariable Long id) {
        return Result.ok(agentService.getAgentDetail(id));
    }

    @Operation(summary = "获取Agent对话能力（按配置版本）")
    @GetMapping("/{id}/chat-capabilities")
    public Result<AgentChatCapabilitiesDTO> getChatCapabilities(
            @PathVariable Long id,
            @RequestParam(required = false) Integer configVersion) {
        return Result.ok(agentService.getChatCapabilities(id, configVersion));
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
            @RequestBody List<String> knowledgeIds) {
        if (knowledgeIds != null && knowledgeIds.size() > 10) {
            throw new BizException(ErrorCode.AGENT_KNOWLEDGE_LIMIT);
        }
        agentService.updateKnowledgeBindings(id, parseBindingIdStrings(knowledgeIds));
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
            @RequestBody List<String> toolIds) {
        if (toolIds != null && toolIds.size() > 10) {
            throw new BizException(ErrorCode.AGENT_TOOL_LIMIT);
        }
        agentService.updateToolBindings(id, parseBindingIdStrings(toolIds));
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
            @RequestBody List<String> mcpServerIds) {
        if (mcpServerIds != null && mcpServerIds.size() > 5) {
            throw new BizException(ErrorCode.AGENT_MCP_LIMIT);
        }
        agentService.updateMcpServerBindings(id, parseBindingIdStrings(mcpServerIds));
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
            @RequestBody List<String> subAgentIds) {
        if (subAgentIds != null && subAgentIds.size() > 5) {
            throw new BizException(ErrorCode.AGENT_SUBAGENT_LIMIT);
        }
        agentService.updateSubAgentBindings(id, parseBindingIdStrings(subAgentIds));
        return Result.ok();
    }

    @Operation(summary = "获取Agent绑定的Skill ID列表")
    @GetMapping("/{id}/skills")
    public Result<List<String>> getSkillIds(@PathVariable Long id) {
        List<Long> skillIds = agentService.getSkillIds(id);
        return Result.ok(skillIds.stream().map(String::valueOf).toList());
    }

    @Operation(summary = "更新Agent绑定的Skill")
    @PutMapping("/{id}/skills")
    public Result<Void> updateSkillBindings(
            @PathVariable Long id,
            @RequestBody List<String> skillIds) {
        if (skillIds != null && skillIds.size() > 10) {
            throw new BizException(ErrorCode.AGENT_SKILL_LIMIT);
        }
        agentService.updateSkillBindings(id, parseBindingIdStrings(skillIds));
        return Result.ok();
    }

    /** 绑定 ID 统一按字符串接收，避免前端 Long 精度丢失 */
    private List<Long> parseBindingIdStrings(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                result.add(Long.parseLong(id.trim()));
            }
        }
        return result;
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

    @Operation(summary = "发布Agent（对话型/助手型）")
    @PostMapping("/{id}/publish")
    public Result<Map<String, Object>> publishAgent(
            @PathVariable Long id,
            @RequestBody(required = false) @Valid AgentPublishRequest body) {
        String description = body != null ? body.getDescription() : null;
        return Result.ok(agentVersionService.publishChatAgent(id, description));
    }

    @Operation(summary = "已发布版本列表")
    @GetMapping("/{id}/versions")
    public Result<List<WorkflowVersionVO>> listVersions(@PathVariable Long id) {
        return Result.ok(agentVersionService.listPublishedVersions(id));
    }

    @Operation(summary = "获取已发布版本配置详情")
    @GetMapping("/{id}/versions/{version}")
    public Result<Map<String, Object>> getVersionDetail(
            @PathVariable Long id,
            @PathVariable Integer version) {
        return Result.ok(agentVersionService.getPublishedVersionDetail(id, version));
    }

    @Operation(summary = "恢复已发布版本到当前编辑态")
    @PostMapping("/{id}/versions/{version}/restore")
    public Result<Void> restoreVersion(@PathVariable Long id, @PathVariable Integer version) {
        agentVersionService.restorePublishedToDraft(id, version);
        return Result.ok();
    }

    @Operation(summary = "删除已发布版本")
    @DeleteMapping("/{id}/versions/{version}")
    public Result<Void> deleteVersion(@PathVariable Long id, @PathVariable Integer version) {
        agentVersionService.deletePublishedVersion(id, version);
        return Result.ok();
    }
}

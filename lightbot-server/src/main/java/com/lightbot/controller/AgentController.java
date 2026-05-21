package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.common.Result;
import com.lightbot.entity.Agent;
import com.lightbot.service.AgentKnowledgeService;
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
    private final AgentKnowledgeService agentKnowledgeService;

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
        agentKnowledgeService.updateKnowledgeBindings(id, knowledgeIds);
        return Result.ok();
    }

    @Operation(summary = "获取Agent绑定的知识库ID列表")
    @GetMapping("/{id}/knowledge")
    public Result<List<Long>> getKnowledgeIds(@PathVariable Long id) {
        return Result.ok(agentKnowledgeService.getKnowledgeIds(id));
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

package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.common.Result;
import com.lightbot.entity.Agent;
import com.lightbot.service.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
            @RequestParam(defaultValue = "50") int pageSize) {
        return Result.ok(agentService.listMyAgents(pageNum, pageSize));
    }

    @Operation(summary = "获取Agent详情")
    @GetMapping("/{id}")
    public Result<Agent> getById(@PathVariable Long id) {
        return Result.ok(agentService.getById(id));
    }

    @Operation(summary = "删除Agent")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        agentService.deleteById(id);
        return Result.ok();
    }
}

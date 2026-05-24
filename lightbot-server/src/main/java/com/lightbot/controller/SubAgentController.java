package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.common.Result;
import com.lightbot.dto.SubAgentRequest;
import com.lightbot.entity.SubAgent;
import com.lightbot.service.SubAgentService;
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

    @Operation(summary = "新增SubAgent")
    @PostMapping
    public Result<SubAgent> create(@Valid @RequestBody SubAgentRequest request) {
        return Result.ok(subAgentService.create(request));
    }

    @Operation(summary = "更新SubAgent")
    @PutMapping
    public Result<SubAgent> update(@Valid @RequestBody SubAgentRequest request) {
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
}
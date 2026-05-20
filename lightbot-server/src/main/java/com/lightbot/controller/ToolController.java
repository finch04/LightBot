package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.common.Result;
import com.lightbot.dto.ToolRequest;
import com.lightbot.entity.Tool;
import com.lightbot.service.ToolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Tool管理", description = "Tool的增删改查")
@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
public class ToolController {

    private final ToolService toolService;

    @Operation(summary = "新增Tool")
    @PostMapping
    public Result<Tool> create(@Valid @RequestBody ToolRequest request) {
        return Result.ok(toolService.create(request));
    }

    @Operation(summary = "更新Tool")
    @PutMapping
    public Result<Tool> update(@Valid @RequestBody ToolRequest request) {
        return Result.ok(toolService.update(request));
    }

    @Operation(summary = "删除Tool")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        toolService.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "分页查询Tool")
    @GetMapping
    public Result<Page<Tool>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(toolService.listPage(pageNum, pageSize));
    }

    @Operation(summary = "获取单个Tool")
    @GetMapping("/{id}")
    public Result<Tool> getById(@PathVariable Long id) {
        return Result.ok(toolService.getById(id));
    }
}

package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.common.Result;
import com.lightbot.dto.McpServerRequest;
import com.lightbot.entity.McpServer;
import com.lightbot.service.McpServerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "MCP Server管理", description = "MCP Server的增删改查")
@RestController
@RequestMapping("/api/mcp-servers")
@RequiredArgsConstructor
public class McpServerController {

    private final McpServerService mcpServerService;

    @Operation(summary = "新增MCP Server")
    @PostMapping
    public Result<McpServer> create(@Valid @RequestBody McpServerRequest request) {
        return Result.ok(mcpServerService.create(request));
    }

    @Operation(summary = "更新MCP Server")
    @PutMapping
    public Result<McpServer> update(@Valid @RequestBody McpServerRequest request) {
        return Result.ok(mcpServerService.update(request));
    }

    @Operation(summary = "删除MCP Server")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mcpServerService.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "分页查询MCP Server")
    @GetMapping
    public Result<Page<McpServer>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(mcpServerService.listPage(pageNum, pageSize));
    }

    @Operation(summary = "获取单个MCP Server")
    @GetMapping("/{id}")
    public Result<McpServer> getById(@PathVariable Long id) {
        return Result.ok(mcpServerService.getById(id));
    }
}

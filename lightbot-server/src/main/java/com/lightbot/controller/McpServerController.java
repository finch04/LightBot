package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.common.Result;
import com.lightbot.dto.McpServerRequest;
import com.lightbot.dto.McpToolVO;
import com.lightbot.entity.McpServer;
import com.lightbot.enums.ErrorCode;
import com.lightbot.service.McpClientService;
import com.lightbot.service.McpServerService;
import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Tag(name = "MCP Server管理", description = "MCP Server的增删改查")
@RestController
@RequestMapping("/api/mcp-servers")
@RequiredArgsConstructor
public class McpServerController {

    private final McpServerService mcpServerService;
    private final McpClientService mcpClientService;
    private final ObjectMapper objectMapper;

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
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String name) {
        return Result.ok(mcpServerService.listPage(pageNum, pageSize, name));
    }

    @Operation(summary = "获取单个MCP Server")
    @GetMapping("/{id}")
    public Result<McpServer> getById(@PathVariable Long id) {
        return Result.ok(mcpServerService.getById(id));
    }

    @Operation(summary = "测试MCP Server连接")
    @PostMapping("/{id}/test")
    public Result<List<McpSchema.Tool>> testConnection(@PathVariable Long id) {
        return Result.ok(mcpClientService.testConnection(id));
    }

    @Operation(summary = "获取MCP Server的工具列表（运行时，含参数详情）")
    @GetMapping("/{id}/tools")
    public Result<List<McpToolVO>> listTools(@PathVariable Long id) {
        McpServer server = mcpServerService.getById(id);
        if (server == null) {
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND);
        }

        // 1. 从缓存或运行时获取工具列表
        List<McpSchema.Tool> mcpTools = mcpClientService.getToolsWithCache(id);

        // 2. 解析 disabled_tools
        Set<String> disabledTools = parseDisabledTools(server.getDisabledTools());

        // 3. 构建 VO 列表
        List<McpToolVO> voList = new ArrayList<>();
        for (McpSchema.Tool tool : mcpTools) {
            McpToolVO vo = new McpToolVO();
            vo.setName(tool.name());
            vo.setDescription(tool.description() != null ? tool.description() : "");
            vo.setEnabled(!disabledTools.contains(tool.name()));
            // 提取参数 Schema
            if (tool.inputSchema() != null) {
                try {
                    vo.setInputSchema(objectMapper.writeValueAsString(tool.inputSchema()));
                } catch (Exception e) {
                    vo.setInputSchema("{}");
                }
            }
            voList.add(vo);
        }
        return Result.ok(voList);
    }

    @Operation(summary = "刷新MCP Server工具（清除缓存重新获取）")
    @PostMapping("/{id}/tools/refresh")
    public Result<List<McpToolVO>> refreshTools(@PathVariable Long id) {
        // 1. 清除缓存
        mcpClientService.clearCache(id);

        // 2. 重新获取工具列表
        return listTools(id);
    }

    @Operation(summary = "启用/禁用MCP Server")
    @PutMapping("/{id}/enabled")
    public Result<Void> setEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        mcpServerService.setEnabled(id, enabled);
        return Result.ok();
    }

    @Operation(summary = "启用/禁用MCP工具")
    @PutMapping("/{id}/tools/{toolName}/toggle")
    public Result<Void> toggleTool(@PathVariable Long id, @PathVariable String toolName) {
        McpServer server = mcpServerService.getById(id);
        if (server == null) {
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND);
        }

        // 1. 解析现有 disabled_tools
        Set<String> disabledTools = parseDisabledTools(server.getDisabledTools());

        // 2. 切换状态
        if (disabledTools.contains(toolName)) {
            disabledTools.remove(toolName);  // 启用
        } else {
            disabledTools.add(toolName);      // 禁用
        }

        // 3. 更新数据库
        try {
            server.setDisabledTools(objectMapper.writeValueAsString(new ArrayList<>(disabledTools)));
            mcpServerService.updateById(server);
            // 4. 清除缓存
            mcpClientService.clearCache(id);
            log.info("[MCP] 工具状态切换: serverId={}, tool={}, enabled={}",
                    id, toolName, !disabledTools.contains(toolName));
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, e);
        }
        return Result.ok();
    }

    /**
     * 解析 disabled_tools JSON 数组
     */
    private Set<String> parseDisabledTools(String disabledToolsJson) {
        if (disabledToolsJson == null || disabledToolsJson.isBlank()) {
            return new HashSet<>();
        }
        try {
            List<String> list = objectMapper.readValue(disabledToolsJson,
                    new com.fasterxml.jackson.core.type.TypeReference<>() {});
            return new HashSet<>(list);
        } catch (Exception e) {
            log.warn("[MCP] 解析disabled_tools失败: {}", e.getMessage());
            return new HashSet<>();
        }
    }
}
package com.lightbot.controller;

import com.lightbot.common.Result;
import com.lightbot.service.ToolCallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 工具调用记录 Controller
 *
 * @author finch
 * @since 2026-05-29
 */
@Tag(name = "工具调用记录", description = "工具调用日志查询接口")
@RestController
@RequestMapping("/api/tool-calls")
@RequiredArgsConstructor
public class ToolCallController {

    private final ToolCallService toolCallService;

    @GetMapping
    @Operation(summary = "分页查询工具调用记录")
    public Result<Map<String, Object>> pageList(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "工具名称") @RequestParam(required = false) String toolName,
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @Parameter(description = "会话ID") @RequestParam(required = false) Long sessionId,
            @Parameter(description = "开始时间") @RequestParam(required = false) String startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) String endTime) {
        return Result.ok(toolCallService.pageList(pageNum, pageSize, toolName, status, sessionId, startTime, endTime));
    }

    /**
     * 批量删除工具调用记录
     */
    @DeleteMapping
    @Operation(summary = "批量删除工具调用记录")
    public Result<Void> deleteToolCalls(@RequestBody List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            toolCallService.removeByIds(ids);
        }
        return Result.ok();
    }
}

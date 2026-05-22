package com.lightbot.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.common.Result;
import com.lightbot.entity.Task;
import com.lightbot.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 任务管理接口
 *
 * @author finch
 * @since 2026-05-21
 */
@Tag(name = "任务管理", description = "任务查询、取消")
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @Operation(summary = "分页查询当前用户的任务列表")
    @GetMapping
    public Result<Page<Task>> list(@RequestParam(defaultValue = "1") int pageNum,
                                    @RequestParam(defaultValue = "10") int pageSize,
                                    @RequestParam(required = false) String name,
                                    @RequestParam(required = false) String status) {
        long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(taskService.listByUserId(userId, pageNum, pageSize, name, status));
    }

    @Operation(summary = "统计当前用户的运行中+待处理任务数")
    @GetMapping("/running-count")
    public Result<Long> runningCount() {
        long userId = StpUtil.getLoginIdAsLong();
        Long running = taskService.countByStatus(userId, "running");
        Long pending = taskService.countByStatus(userId, "pending");
        return Result.ok(running + pending);
    }

    @Operation(summary = "获取任务详情")
    @GetMapping("/{taskId}")
    public Result<Task> getById(@PathVariable Long taskId) {
        long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(taskService.getTaskById(taskId, userId));
    }

    @Operation(summary = "取消任务")
    @PostMapping("/{taskId}/cancel")
    public Result<Void> cancel(@PathVariable Long taskId) {
        long userId = StpUtil.getLoginIdAsLong();
        // 先校验归属
        taskService.getTaskById(taskId, userId);
        boolean success = taskService.requestCancel(taskId);
        if (!success) {
            return Result.fail(61002, "任务无法取消（可能已完成或已取消）");
        }
        return Result.ok();
    }

    @Operation(summary = "删除任务")
    @DeleteMapping("/{taskId}")
    public Result<Void> delete(@PathVariable Long taskId) {
        long userId = StpUtil.getLoginIdAsLong();
        taskService.deleteTask(taskId, userId);
        return Result.ok();
    }
}

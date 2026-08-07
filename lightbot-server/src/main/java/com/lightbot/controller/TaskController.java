package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.common.Result;
import com.lightbot.entity.Task;
import com.lightbot.service.TaskService;
import com.lightbot.task.ProgressSnapshot;
import com.lightbot.task.TaskQueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 任务管理接口（企业共享）
 *
 * @author finch
 * @since 2026-05-21
 */
@Tag(name = "任务管理", description = "企业任务查询、取消")
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final TaskQueueService taskQueueService;

    @Operation(summary = "分页查询企业任务列表")
    @GetMapping
    public Result<Page<Task>> list(@RequestParam(defaultValue = "1") int pageNum,
                                    @RequestParam(defaultValue = "10") int pageSize,
                                    @RequestParam(required = false) String name,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String type) {
        return Result.ok(taskService.listByUserId(null, pageNum, pageSize, name, status, type));
    }

    @Operation(summary = "统计企业运行中+待处理任务数")
    @GetMapping("/running-count")
    public Result<Long> runningCount() {
        Long running = taskService.countByStatus(null, "running");
        Long pending = taskService.countByStatus(null, "pending");
        Long pendingRetry = taskService.countByStatus(null, "pending_retry");
        return Result.ok(running + pending + pendingRetry);
    }

    @Operation(summary = "按类型统计企业进行中任务数")
    @GetMapping("/type-counts")
    public Result<Map<String, Long>> typeCounts() {
        return Result.ok(taskService.countByType(null));
    }

    @Operation(summary = "获取任务详情（进度优先读 Redis Hash，毫秒级可达）")
    @GetMapping("/{taskId}")
    public Result<Task> getById(@PathVariable Long taskId) {
        Task task = taskService.getTaskById(taskId, null);
        ProgressSnapshot snapshot = taskQueueService.getProgress(taskId);
        if (snapshot != null && snapshot.getProgress() > task.getProgress()) {
            task.setProgress(snapshot.getProgress());
            if (snapshot.getMessage() != null && !snapshot.getMessage().isEmpty()) {
                task.setMessage(snapshot.getMessage());
            }
        }
        return Result.ok(task);
    }

    @Operation(summary = "获取任务实时进度快照（Redis Hash 直读）")
    @GetMapping("/{taskId}/progress")
    public Result<ProgressSnapshot> getProgress(@PathVariable Long taskId) {
        taskService.getTaskById(taskId, null);
        return Result.ok(taskQueueService.getProgress(taskId));
    }

    @Operation(summary = "取消任务")
    @PostMapping("/{taskId}/cancel")
    public Result<Void> cancel(@PathVariable Long taskId) {
        taskService.getTaskById(taskId, null);
        boolean success = taskService.requestCancel(taskId);
        if (!success) {
            return Result.fail(61002, "任务无法取消（可能已完成或已取消）");
        }
        return Result.ok();
    }

    @Operation(summary = "删除任务")
    @DeleteMapping("/{taskId}")
    public Result<Void> delete(@PathVariable Long taskId) {
        taskService.deleteTask(taskId, null);
        return Result.ok();
    }
}

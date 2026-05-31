package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.controller.TaskEventController;
import com.lightbot.entity.Task;
import com.lightbot.enums.ErrorCode;
import org.springframework.util.StringUtils;
import com.lightbot.enums.TaskStatus;
import com.lightbot.enums.TaskType;
import com.lightbot.mapper.TaskMapper;
import com.lightbot.service.TaskService;
import com.lightbot.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务队列服务实现
 *
 * @author finch
 * @since 2026-05-21
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl extends ServiceImpl<TaskMapper, Task>
        implements TaskService {

    private final RedisUtil redisUtil;
    /** 延迟获取，避免与 TaskEventController 循环依赖 */
    private final ObjectProvider<TaskEventController> taskEventProvider;

    @Override
    public Task createTask(TaskType type, String name, Long userId, Long refId, String payload) {
        Task task = new Task();
        task.setName(name);
        task.setType(type);
        task.setStatus(TaskStatus.PENDING);
        task.setProgress(0);
        task.setCancelRequested(0);
        task.setUserId(userId);
        task.setRefId(refId);
        task.setPayload(payload);
        save(task);

        // 推入Redis队列
        redisUtil.pushTask(task.getId().toString());
        log.info("[任务] 创建成功, taskId={}, type={}, name={}", task.getId(), type, name);

        // 推送任务计数变更
        broadcastTaskCount(userId);
        return task;
    }

    @Override
    public void updateProgress(Long taskId, int progress, String message) {
        lambdaUpdate()
                .eq(Task::getId, taskId)
                .set(Task::getProgress, progress)
                .set(Task::getMessage, message)
                .set(Task::getUpdateTime, LocalDateTime.now())
                .update();
    }

    @Override
    public void markRunning(Long taskId) {
        lambdaUpdate()
                .eq(Task::getId, taskId)
                .set(Task::getStatus, TaskStatus.RUNNING)
                .set(Task::getStartedAt, LocalDateTime.now())
                .set(Task::getUpdateTime, LocalDateTime.now())
                .update();
        broadcastTaskCountByTaskId(taskId);
    }

    @Override
    public void markSuccess(Long taskId, String result) {
        lambdaUpdate()
                .eq(Task::getId, taskId)
                .set(Task::getStatus, TaskStatus.SUCCESS)
                .set(Task::getProgress, 100)
                .set(Task::getResult, result)
                .set(Task::getCompletedAt, LocalDateTime.now())
                .set(Task::getUpdateTime, LocalDateTime.now())
                .update();
        log.info("[任务] 执行成功, taskId={}", taskId);
        broadcastTaskCountByTaskId(taskId);
    }

    @Override
    public void markFailed(Long taskId, String error) {
        lambdaUpdate()
                .eq(Task::getId, taskId)
                .set(Task::getStatus, TaskStatus.FAILED)
                .set(Task::getError, error)
                .set(Task::getCompletedAt, LocalDateTime.now())
                .set(Task::getUpdateTime, LocalDateTime.now())
                .update();
        log.warn("[任务] 执行失败, taskId={}, error={}", taskId, error);
        broadcastTaskCountByTaskId(taskId);
    }

    @Override
    public void markCancelled(Long taskId, String message) {
        lambdaUpdate()
                .eq(Task::getId, taskId)
                .set(Task::getStatus, TaskStatus.CANCELLED)
                .set(Task::getError, message)
                .set(Task::getCompletedAt, LocalDateTime.now())
                .set(Task::getUpdateTime, LocalDateTime.now())
                .update();
        log.info("[任务] 已取消, taskId={}, message={}", taskId, message);
        broadcastTaskCountByTaskId(taskId);
    }

    @Override
    public boolean requestCancel(Long taskId) {
        boolean update = lambdaUpdate()
                .eq(Task::getId, taskId)
                .in(Task::getStatus, TaskStatus.PENDING, TaskStatus.RUNNING)
                .set(Task::getCancelRequested, 1)
                .set(Task::getUpdateTime, LocalDateTime.now())
                .update();
        return update;
    }

    @Override
    public Page<Task> listByUserId(Long userId, int pageNum, int pageSize, String name, String status, String type) {
        TaskType taskType = StringUtils.hasText(type) ? TaskType.fromValue(type) : null;
        return baseMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getUserId, userId)
                        .like(StringUtils.hasText(name), Task::getName, name)
                        .eq(StringUtils.hasText(status) && !"active".equals(status), Task::getStatus, status)
                        .in(StringUtils.hasText(status) && "active".equals(status), Task::getStatus, List.of("pending", "running"))
                        .eq(taskType != null, Task::getType, taskType)
                        .orderByDesc(Task::getCreateTime));
    }

    @Override
    public Task getTaskById(Long taskId, Long userId) {
        Task task = getById(taskId);
        if (task == null || !task.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.TASK_NOT_FOUND);
        }
        return task;
    }

    @Override
    public Long countByStatus(Long userId, String status) {
        return count(new LambdaQueryWrapper<Task>()
                .eq(Task::getUserId, userId)
                .eq(Task::getStatus, status));
    }

    @Override
    public Map<String, Long> countByType(Long userId) {
        // 查询所有进行中+等待中的任务，按类型分组计数
        List<Task> tasks = list(new LambdaQueryWrapper<Task>()
                .eq(Task::getUserId, userId)
                .in(Task::getStatus, List.of(TaskStatus.PENDING, TaskStatus.RUNNING)));
        return tasks.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        t -> t.getType().getDesc(),
                        java.util.stream.Collectors.counting()));
    }

    @Override
    public void deleteTask(Long taskId, Long userId) {
        Task task = getTaskById(taskId, userId);

        // 仅已终态任务可删除
        boolean isTerminal = task.getStatus() == TaskStatus.SUCCESS
                || task.getStatus() == TaskStatus.FAILED
                || task.getStatus() == TaskStatus.CANCELLED;
        if (!isTerminal) {
            throw new BizException(ErrorCode.TASK_DELETE_FAILED);
        }

        removeById(taskId);
        log.info("[任务] 删除成功, taskId={}, userId={}", taskId, userId);
    }

    /** 推送任务计数变更给指定用户 */
    private void broadcastTaskCount(Long userId) {
        try {
            taskEventProvider.getObject().pushToUser(userId);
        } catch (Exception e) {
            // 推送失败不影响主流程
        }
    }

    /** 通过 taskId 查询 userId 后推送 */
    private void broadcastTaskCountByTaskId(Long taskId) {
        Task task = getById(taskId);
        if (task != null) {
            broadcastTaskCount(task.getUserId());
        }
    }
}

package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.entity.Task;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.TaskStatus;
import com.lightbot.enums.TaskType;
import com.lightbot.mapper.TaskMapper;
import com.lightbot.service.TaskService;
import com.lightbot.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

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
        return task;
    }

    @Override
    public void updateProgress(Long taskId, int progress, String message) {
        lambdaUpdate()
                .eq(Task::getId, taskId)
                .set(Task::getProgress, progress)
                .set(Task::getMessage, message)
                .update();
    }

    @Override
    public void markRunning(Long taskId) {
        lambdaUpdate()
                .eq(Task::getId, taskId)
                .set(Task::getStatus, TaskStatus.RUNNING)
                .set(Task::getStartedAt, LocalDateTime.now())
                .update();
    }

    @Override
    public void markSuccess(Long taskId, String result) {
        lambdaUpdate()
                .eq(Task::getId, taskId)
                .set(Task::getStatus, TaskStatus.SUCCESS)
                .set(Task::getProgress, 100)
                .set(Task::getResult, result)
                .set(Task::getCompletedAt, LocalDateTime.now())
                .update();
        log.info("[任务] 执行成功, taskId={}", taskId);
    }

    @Override
    public void markFailed(Long taskId, String error) {
        lambdaUpdate()
                .eq(Task::getId, taskId)
                .set(Task::getStatus, TaskStatus.FAILED)
                .set(Task::getError, error)
                .set(Task::getCompletedAt, LocalDateTime.now())
                .update();
        log.warn("[任务] 执行失败, taskId={}, error={}", taskId, error);
    }

    @Override
    public boolean requestCancel(Long taskId) {
        boolean update = lambdaUpdate()
                .eq(Task::getId, taskId)
                .in(Task::getStatus, TaskStatus.PENDING, TaskStatus.RUNNING)
                .set(Task::getCancelRequested, 1)
                .update();
        return update;
    }

    @Override
    public Page<Task> listByUserId(Long userId, int pageNum, int pageSize) {
        return page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getUserId, userId)
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
}

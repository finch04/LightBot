package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.entity.Task;
import com.lightbot.enums.ErrorCode;
import com.lightbot.task.ProgressSnapshot;
import com.lightbot.task.RetryPolicy;
import com.lightbot.task.RetryPolicyProperties;
import com.lightbot.task.TaskQueueService;
import org.springframework.util.StringUtils;
import com.lightbot.enums.TaskStatus;
import com.lightbot.enums.TaskType;
import com.lightbot.mapper.TaskMapper;
import com.lightbot.service.TaskService;
import com.lightbot.service.port.TaskCountNotifier;
import com.lightbot.service.port.TaskInterruptPort;
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

    private final TaskQueueService taskQueueService;
    private final RetryPolicyProperties retryPolicyProperties;
    private final ObjectProvider<TaskCountNotifier> taskCountNotifier;
    private final ObjectProvider<TaskInterruptPort> taskInterruptPort;

    @Override
    public Task createTask(TaskType type, String name, Long userId, Long refId, String payload) {
        // 1. 初始化任务记录（attempts=0、max_attempts 来自重试策略、status=PENDING）
        RetryPolicy policy = retryPolicyProperties.resolve(type);
        Task task = new Task();
        task.setName(name);
        task.setType(type);
        task.setStatus(TaskStatus.PENDING);
        task.setProgress(0);
        task.setCancelRequested(0);
        task.setUserId(userId);
        task.setRefId(refId);
        task.setPayload(payload);
        task.setAttempts(0);
        task.setMaxAttempts(policy.getMaxAttempts());
        task.setDeadLetter(0);
        save(task);

        // 2. XADD 投递到主 Stream（投递后回写 streamId，便于 PEL/XCLAIM 追溯）
        String streamId = taskQueueService.enqueue(task);
        lambdaUpdate()
                .eq(Task::getId, task.getId())
                .set(Task::getStreamId, streamId)
                .update();

        log.info("[任务] 创建成功, taskId={}, type={}, name={}, streamId={}", task.getId(), type, name, streamId);

        broadcastTaskCount(userId);
        return task;
    }

    @Override
    public void updateProgress(Long taskId, int progress, String message) {
        // 同步写 DB（持久化兜底）+ Redis Hash（前端毫秒级读取）
        lambdaUpdate()
                .eq(Task::getId, taskId)
                .set(Task::getProgress, progress)
                .set(Task::getMessage, message)
                .set(Task::getUpdateTime, LocalDateTime.now())
                .update();
        try {
            taskQueueService.reportProgress(taskId, progress, message);
        } catch (Exception e) {
            // Hash 写失败不影响主流程，前端仍可读 DB
            log.debug("[任务] 进度 Hash 写失败: taskId={}, err={}", taskId, e.getMessage());
        }
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
    public boolean markStart(Long taskId, int attempts, String streamId) {
        // CAS：仅 PENDING / PENDING_RETRY 可转入 RUNNING，避免 cg:default 与 cg:heavy 同时执行同一任务
        boolean updated = lambdaUpdate()
                .eq(Task::getId, taskId)
                .in(Task::getStatus, TaskStatus.PENDING, TaskStatus.PENDING_RETRY)
                .set(Task::getStatus, TaskStatus.RUNNING)
                .set(Task::getAttempts, attempts)
                .set(Task::getStreamId, streamId)
                .set(Task::getStartedAt, LocalDateTime.now())
                .set(Task::getUpdateTime, LocalDateTime.now())
                .update();
        if (updated) {
            broadcastTaskCountByTaskId(taskId);
        }
        return updated;
    }

    @Override
    public void markPendingRetry(Long taskId, int attempts, LocalDateTime nextRetryAt, String error) {
        lambdaUpdate()
                .eq(Task::getId, taskId)
                .set(Task::getStatus, TaskStatus.PENDING_RETRY)
                .set(Task::getAttempts, attempts)
                .set(Task::getNextRetryAt, nextRetryAt)
                .set(Task::getError, truncate(error, 500))
                .set(Task::getUpdateTime, LocalDateTime.now())
                .update();
        log.info("[任务] 等待重试, taskId={}, attempts={}, nextRetryAt={}", taskId, attempts, nextRetryAt);
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
                .set(Task::getError, truncate(error, 2000))
                .set(Task::getCompletedAt, LocalDateTime.now())
                .set(Task::getUpdateTime, LocalDateTime.now())
                .update();
        log.warn("[任务] 执行失败, taskId={}, error={}", taskId, error);
        broadcastTaskCountByTaskId(taskId);
    }

    @Override
    public void markDeadLetter(Long taskId, String error) {
        lambdaUpdate()
                .eq(Task::getId, taskId)
                .set(Task::getDeadLetter, 1)
                .set(Task::getError, truncate(error, 2000))
                .update();
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
        // 1. DB 标记
        boolean update = lambdaUpdate()
                .eq(Task::getId, taskId)
                .in(Task::getStatus, TaskStatus.PENDING, TaskStatus.PENDING_RETRY, TaskStatus.RUNNING)
                .set(Task::getCancelRequested, 1)
                .set(Task::getUpdateTime, LocalDateTime.now())
                .update();
        if (update) {
            // 2. Redis 信号（executor O(1) 快速检测）
            taskQueueService.publishCancel(taskId);
            // 3. 中断执行线程（打断阻塞的 LLM 调用等 IO）
            try {
                taskInterruptPort.getObject().interrupt(taskId);
            } catch (Exception e) {
                log.debug("[任务] 中断线程失败(可能已结束), taskId={}", taskId);
            }
        }
        return update;
    }

    @Override
    public Page<Task> listByUserId(Long userId, int pageNum, int pageSize, String name, String status, String type) {
        // 企业资产：userId 为空时查全企业任务（触发人仅作审计）
        TaskType taskType = StringUtils.hasText(type) ? TaskType.fromValue(type) : null;
        return baseMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Task>()
                        .eq(userId != null, Task::getUserId, userId)
                        .like(StringUtils.hasText(name), Task::getName, name)
                        .eq(StringUtils.hasText(status) && !"active".equals(status), Task::getStatus, status)
                        .in(StringUtils.hasText(status) && "active".equals(status), Task::getStatus,
                                List.of("pending", "pending_retry", "running"))
                        .eq(taskType != null, Task::getType, taskType)
                        .orderByDesc(Task::getCreateTime));
    }

    @Override
    public Task getTaskById(Long taskId, Long userId) {
        Task task = getById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.TASK_NOT_FOUND);
        }
        return task;
    }

    @Override
    public Long countByStatus(Long userId, String status) {
        return count(new LambdaQueryWrapper<Task>()
                .eq(userId != null, Task::getUserId, userId)
                .eq(Task::getStatus, status));
    }

    @Override
    public Map<String, Long> countByType(Long userId) {
        List<Task> tasks = list(new LambdaQueryWrapper<Task>()
                .eq(userId != null, Task::getUserId, userId)
                .in(Task::getStatus, List.of(TaskStatus.PENDING, TaskStatus.PENDING_RETRY, TaskStatus.RUNNING)));
        return tasks.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        t -> t.getType().getDesc(),
                        java.util.stream.Collectors.counting()));
    }

    @Override
    public void deleteTask(Long taskId, Long userId) {
        Task task = getTaskById(taskId, userId);
        boolean isTerminal = task.getStatus() == TaskStatus.SUCCESS
                || task.getStatus() == TaskStatus.FAILED
                || task.getStatus() == TaskStatus.CANCELLED;
        if (!isTerminal) {
            throw new BizException(ErrorCode.TASK_DELETE_FAILED);
        }
        removeById(taskId);
        log.info("[任务] 删除成功, taskId={}, triggeredBy={}", taskId, task.getUserId());
        broadcastTaskCount(null);
    }

    /** 推送企业任务计数变更给所有在线建设者 */
    private void broadcastTaskCount(Long userId) {
        try {
            taskCountNotifier.getObject().notifyAllUsers();
        } catch (Exception e) {
            // 推送失败不影响主流程
        }
    }

    /** 任务状态变更后广播企业计数 */
    private void broadcastTaskCountByTaskId(Long taskId) {
        broadcastTaskCount(null);
    }

    /** 截断字符串到指定长度，避免 DB 字段超长 */
    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}

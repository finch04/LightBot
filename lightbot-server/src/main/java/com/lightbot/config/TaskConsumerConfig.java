package com.lightbot.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lightbot.entity.Document;
import com.lightbot.entity.Task;
import com.lightbot.enums.DocumentStatus;
import com.lightbot.enums.TaskStatus;
import com.lightbot.enums.TaskType;
import com.lightbot.service.DocumentService;
import com.lightbot.service.TaskService;
import com.lightbot.task.TaskExecutor;
import com.lightbot.util.RedisUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 任务消费者配置，启动线程池从Redis队列消费任务
 *
 * @author finch
 * @since 2026-05-21
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class TaskConsumerConfig {

    private final RedisUtil redisUtil;
    private final TaskService taskService;
    private final DocumentService documentService;
    private final ApplicationContext applicationContext;

    @Value("${lightbot.task.consumer.pool-size:2}")
    private int poolSize;

    /** 孤儿任务判定阈值：updateTime 超过此时间的 RUNNING 任务视为孤儿 */
    private static final int ORPHAN_TIMEOUT_MINUTES = 10;

    private ExecutorService executorService;
    private final AtomicBoolean running = new AtomicBoolean(true);

    @PostConstruct
    public void start() {
        // 启动前清理孤儿任务，防止重启后任务永久卡死
        recoverOrphanTasks();

        executorService = Executors.newFixedThreadPool(poolSize);
        for (int i = 0; i < poolSize; i++) {
            final int workerId = i;
            executorService.submit(() -> consumeLoop(workerId));
        }
        log.info("[任务消费者] 启动, poolSize={}", poolSize);
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (executorService != null) {
            executorService.shutdownNow();
        }
        log.info("[任务消费者] 已停止");
    }

    /**
     * 启动时恢复孤儿任务：将超时的 RUNNING 任务标记为 FAILED，
     * 并回滚关联的 Document 状态，解除 PENDING/PROCESSING 的死锁
     */
    private void recoverOrphanTasks() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(ORPHAN_TIMEOUT_MINUTES);
        List<Task> orphans = taskService.list(new LambdaQueryWrapper<Task>()
                .eq(Task::getStatus, TaskStatus.RUNNING)
                .lt(Task::getUpdateTime, threshold));

        if (orphans.isEmpty()) {
            return;
        }

        log.warn("[任务恢复] 发现 {} 个孤儿任务，开始清理...", orphans.size());
        // 需要回滚 Document 状态的任务类型
        Set<TaskType> documentTaskTypes = Set.of(TaskType.DOCUMENT_UPLOAD, TaskType.DOCUMENT_INGEST);

        for (Task task : orphans) {
            try {
                // 1. 标记 Task 为 FAILED
                taskService.markFailed(task.getId(), "系统重启，任务中断，请重新执行");

                // 2. 回滚关联的 Document 状态
                if (documentTaskTypes.contains(task.getType()) && task.getRefId() != null) {
                    recoverDocument(task.getRefId());
                }

                log.info("[任务恢复] 已清理孤儿任务: taskId={}, type={}", task.getId(), task.getType());
            } catch (Exception e) {
                log.error("[任务恢复] 清理孤儿任务失败: taskId={}", task.getId(), e);
            }
        }

        log.info("[任务恢复] 孤儿任务清理完成, count={}", orphans.size());
    }

    /**
     * 回滚文档状态为 FAILED，仅当文档仍处于中间态（UPLOADING/PENDING/PROCESSING）时执行
     */
    private void recoverDocument(Long documentId) {
        Document doc = documentService.getById(documentId);
        if (doc == null) {
            return;
        }
        // 仅回滚中间态文档，已终态（UPLOADED/COMPLETED/FAILED）不动
        DocumentStatus status = doc.getStatus();
        if (status == DocumentStatus.UPLOADED || status == DocumentStatus.COMPLETED
                || status == DocumentStatus.FAILED) {
            return;
        }
        doc.setStatus(DocumentStatus.FAILED);
        doc.setErrorMessage("系统重启，任务中断，请重新入库");
        documentService.updateById(doc);
    }

    private void consumeLoop(int workerId) {
        while (running.get()) {
            try {
                // 1. 阻塞弹出任务ID，超时5秒后重试
                String taskIdStr = redisUtil.popTask(5);
                if (taskIdStr == null) {
                    continue;
                }

                Long taskId = Long.parseLong(taskIdStr);
                Task task = taskService.getById(taskId);
                if (task == null) {
                    log.warn("[任务消费者] 任务不存在, taskId={}", taskId);
                    continue;
                }

                // 2. 检查任务状态（只处理PENDING状态）
                if (task.getStatus() != TaskStatus.PENDING) {
                    log.info("[任务消费者] 跳过非PENDING任务, taskId={}, status={}", taskId, task.getStatus());
                    continue;
                }

                // 3. 标记为执行中
                taskService.markRunning(taskId);
                log.info("[任务消费者] 开始执行, workerId={}, taskId={}, type={}", workerId, taskId, task.getType());

                // 4. 根据任务类型路由到对应执行器
                TaskType taskType = task.getType();
                TaskExecutor executor = getExecutor(taskType);
                if (executor == null) {
                    taskService.markFailed(taskId, "不支持的任务类型: " + taskType);
                    continue;
                }

                try {
                    String result = executor.execute(task);
                    taskService.markSuccess(taskId, result);
                } catch (Exception e) {
                    // 构建详细的错误信息
                    String error = buildErrorMessage(e);
                    log.error("[任务消费者] 执行失败, taskId={}, error={}", taskId, error, e);
                    // 区分用户取消和其他失败
                    if ("任务已被用户取消".equals(e.getMessage())) {
                        taskService.markCancelled(taskId, "任务被用户取消");
                    } else {
                        taskService.markFailed(taskId, error);
                    }
                }

            } catch (Exception e) {
                log.error("[任务消费者] 异常, workerId={}", workerId, e);
            }
        }
    }

    /**
     * 根据 TaskType 的 beanName 从 Spring 容器获取执行器
     */
    private TaskExecutor getExecutor(TaskType taskType) {
        try {
            return applicationContext.getBean(taskType.getBeanName(), TaskExecutor.class);
        } catch (Exception e) {
            log.error("[任务消费者] 获取执行器失败, beanName={}", taskType.getBeanName(), e);
            return null;
        }
    }

    /**
     * 构建详细的错误信息（对NPE等getMessage为null的异常做特殊处理）
     */
    private String buildErrorMessage(Exception e) {
        String msg = e.getMessage();
        if (msg != null && !msg.isBlank()) {
            return msg;
        }
        // NPE 等 getMessage 为 null 的异常，尝试从 cause 或堆栈提取信息
        Throwable cause = e.getCause();
        if (cause != null && cause.getMessage() != null) {
            return e.getClass().getSimpleName() + ": " + cause.getMessage();
        }
        // 从堆栈中提取异常发生位置
        StackTraceElement[] stackTrace = e.getStackTrace();
        if (stackTrace.length > 0) {
            StackTraceElement top = stackTrace[0];
            return e.getClass().getSimpleName() + " at " + top.getClassName() + "." + top.getMethodName()
                    + "(" + top.getFileName() + ":" + top.getLineNumber() + ")";
        }
        return e.getClass().getSimpleName();
    }
}

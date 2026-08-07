package com.lightbot.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lightbot.common.task.FatalTaskException;
import com.lightbot.common.task.RetryableTaskException;
import com.lightbot.common.task.TaskCancelledException;
import com.lightbot.entity.Task;
import com.lightbot.enums.TaskStatus;
import com.lightbot.enums.TaskType;
import com.lightbot.service.TaskService;
import com.lightbot.service.port.TaskInterruptPort;
import com.lightbot.task.RetryPolicy;
import com.lightbot.task.RetryPolicyProperties;
import com.lightbot.task.TaskExecutor;
import com.lightbot.task.TaskMessage;
import com.lightbot.task.TaskQueueService;
import com.lightbot.util.ModelErrorClassifier;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 任务消费者配置：基于 Redis Stream 消费者组的多 worker 拉取/路由
 * <p>每组（cg:default / cg:heavy）独立线程池消费，对应 {@link TaskType.Group}。
 * 替换原 BLPOP 单 List 实现，引入 PEL 自动兜底（共享 consumer name + ID-0 双拉）。
 *
 * <p>崩溃恢复机制：
 * <ul>
 *   <li>同组所有 worker 共享 consumer name（host-group），PEL 也是共享的</li>
 *   <li>每轮先 XREADGROUP ID 0 拉自己 PEL（兜底未 ACK 的崩溃消息）</li>
 *   <li>再 XREADGROUP &gt; BLOCK 5s 拉新消息</li>
 * </ul>
 *
 * @author finch
 * @since 2026-05-21
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class TaskConsumerConfig implements TaskInterruptPort {

    private final TaskQueueService taskQueueService;
    private final TaskService taskService;
    private final ApplicationContext applicationContext;
    private final RetryPolicyProperties retryPolicyProperties;

    /** 默认组（短任务）线程池大小 */
    @Value("${lightbot.task.consumer.pool-size:2}")
    private int defaultPoolSize;

    /** 重型组（长任务：图谱抽取、问答对生成）线程池大小 */
    @Value("${lightbot.task.consumer.heavy-pool-size:1}")
    private int heavyPoolSize;

    /** 阻塞拉取时长（秒），无消息时 BLOCK 时长 */
    private static final long BLOCK_TIMEOUT_SECONDS = 5L;

    private ExecutorService defaultPool;
    private ExecutorService heavyPool;
    private final AtomicBoolean running = new AtomicBoolean(true);

    /** 正在执行的任务线程映射，用于取消时中断 */
    private final ConcurrentHashMap<Long, Thread> runningTasks = new ConcurrentHashMap<>();

    @PostConstruct
    public void start() {
        // 启动时恢复所有未完结任务（PENDING/RUNNING/PENDING_RETRY），重置为 PENDING 并重新入队
        // 必须在 worker 线程池启动前执行，避免和消费者并发处理同一任务
        recoverUnfinishedTasks();

        defaultPool = Executors.newFixedThreadPool(defaultPoolSize);
        heavyPool = Executors.newFixedThreadPool(heavyPoolSize);

        for (int i = 0; i < defaultPoolSize; i++) {
            defaultPool.submit(() -> consumeLoop(TaskType.Group.DEFAULT));
        }
        for (int i = 0; i < heavyPoolSize; i++) {
            heavyPool.submit(() -> consumeLoop(TaskType.Group.HEAVY));
        }
        log.info("[任务消费者] 启动, defaultPoolSize={}, heavyPoolSize={}", defaultPoolSize, heavyPoolSize);
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (defaultPool != null) {
            defaultPool.shutdownNow();
        }
        if (heavyPool != null) {
            heavyPool.shutdownNow();
        }
        log.info("[任务消费者] 已停止");
    }

    /**
     * 中断正在执行的任务线程（配合取消信号，实现快速取消）
     */
    @Override
    public void interrupt(Long taskId) {
        Thread t = runningTasks.get(taskId);
        if (t != null) {
            t.interrupt();
            log.info("[任务消费者] 已中断任务线程, taskId={}", taskId);
        }
    }

    /**
     * 启动时恢复未完结任务
     * <p>扫描所有 PENDING / RUNNING / PENDING_RETRY 任务，重置为 PENDING 并重新入队，
     * 由消费者按正常流程处理（已完结、已取消等终态任务因状态校验会被自动 ACK 跳过）。
     *
     * <p>覆盖场景：
     * <ul>
     *   <li>PENDING：未投递或上次启动前未消费完</li>
     *   <li>RUNNING：上次执行过程中进程崩溃/被 kill，状态卡死</li>
     *   <li>PENDING_RETRY：等待退避重试中进程退出</li>
     * </ul>
     *
     * <p>幂等性：每条任务独立 try-catch，入队失败则 markFailed + 记录错误，便于运维跟进。
     */
    private void recoverUnfinishedTasks() {
        List<Task> unfinished = taskService.list(new LambdaQueryWrapper<Task>()
                .in(Task::getStatus,
                        TaskStatus.PENDING,
                        TaskStatus.RUNNING,
                        TaskStatus.PENDING_RETRY));

        if (unfinished.isEmpty()) {
            log.info("[任务恢复] 启动时无未完结任务");
            return;
        }

        log.warn("[任务恢复] 发现 {} 个未完结任务，开始重新入队", unfinished.size());

        // 计数：重新入队成功 / 失败
        int recovered = 0;
        int failed = 0;
        for (Task task : unfinished) {
            // 记录原始状态用于失败日志，重置后丢失
            TaskStatus prevStatus = task.getStatus();
            try {
                // 重置为 PENDING：清空 streamId/error，让消费者从头执行
                task.setStatus(TaskStatus.PENDING);
                task.setStreamId(null);
                task.setError(null);

                // XADD 重新入队，新 streamId 落库
                String newStreamId = taskQueueService.enqueue(task);
                task.setStreamId(newStreamId);
                taskService.updateById(task);

                recovered++;
                log.info("[任务恢复] taskId={} type={} prevStatus={} 重新入队成功, newStreamId={}",
                        task.getId(), task.getType(), prevStatus, newStreamId);
            } catch (Exception e) {
                log.error("[任务恢复] taskId={} type={} prevStatus={} 重新入队失败: {}",
                        task.getId(), task.getType(), prevStatus, e.getMessage(), e);
                // 标记 FAILED 便于运维跟进，markFailed 本身失败只记日志避免遮蔽原始异常
                try {
                    taskService.markFailed(task.getId(),
                            "系统启动恢复时重新入队失败：" + e.getMessage());
                } catch (Exception markFailedEx) {
                    log.error("[任务恢复] taskId={} markFailed 也异常: {}",
                            task.getId(), markFailedEx.getMessage());
                }
                failed++;
            }
        }

        log.warn("[任务恢复] 完成, total={}, recovered={}, failed={}",
                unfinished.size(), recovered, failed);
    }

    /**
     * 消费循环：每轮先拉本组 PEL（兜底崩溃消息），再拉新消息
     * <p>同组所有 worker 共享固定 consumer name（host-group），因此 PEL 是共享的：
     * 某个 worker 拉到消息但未 ACK 就崩溃，下个 worker 的 ID-0 拉取会重新拿到。
     */
    private void consumeLoop(TaskType.Group group) {
        String consumerName = sharedConsumerName(group);
        boolean redisWarned = false;
        log.info("[任务消费者] worker 启动, group={}, consumer={}", group, consumerName);

        while (running.get()) {
            try {
                // 1. 先尝试拉本组 PEL（XREADGROUP ID 0）：兜底崩溃前未 ACK 的消息
                List<TaskMessage> messages = taskQueueService.readPending(group, consumerName, 1);
                if (messages.isEmpty()) {
                    // 2. PEL 空则拉新消息（XREADGROUP > BLOCK 5000）
                    messages = taskQueueService.readMessages(
                            group, consumerName, 1, Duration.ofSeconds(BLOCK_TIMEOUT_SECONDS));
                }
                if (messages.isEmpty()) {
                    continue;
                }
                redisWarned = false;

                for (TaskMessage msg : messages) {
                    handleMessage(msg, group);
                }
            } catch (Exception e) {
                if (!running.get()) {
                    break;
                }
                if (!redisWarned) {
                    log.warn("[任务消费者] Redis 异常，等待重试: group={}, error={}", group, e.getMessage());
                    redisWarned = true;
                }
                sleepQuietly(5_000L);
            }
        }
        log.info("[任务消费者] worker 退出, group={}, consumer={}", group, consumerName);
    }

    /** 同组共享 consumer name，PEL 也是共享的；多实例下加 host 前缀区分 */
    private String sharedConsumerName(TaskType.Group group) {
        String host = System.getenv().getOrDefault("HOSTNAME", "local");
        return host + "-" + group.name().toLowerCase();
    }

    /**
     * 处理单条消息：状态校验 → 取消检查 → 路由执行 → 异常分级路由
     * <p>包级可见以便单测直接调用，绕过 consumeLoop</p>
     */
    void handleMessage(TaskMessage msg, TaskType.Group group) {
        Long taskId = msg.getTaskId();
        String streamId = msg.getStreamId();

        Task task;
        try {
            task = taskService.getById(taskId);
        } catch (Exception e) {
            log.error("[任务消费者] 加载任务失败, taskId={}, streamId={}", taskId, streamId, e);
            safeAck(streamId);
            return;
        }

        if (task == null) {
            log.warn("[任务消费者] 任务不存在, taskId={}, streamId={}", taskId, streamId);
            safeAck(streamId);
            return;
        }

        // 1. 状态校验：只处理 PENDING / PENDING_RETRY；其他状态直接 ACK（防重复消费）
        if (task.getStatus() != TaskStatus.PENDING && task.getStatus() != TaskStatus.PENDING_RETRY) {
            log.info("[任务消费者] 跳过非活跃任务, taskId={}, status={}", taskId, task.getStatus());
            safeAck(streamId);
            return;
        }

        // 1.1 按任务分组过滤：main stream 上 cg:default / cg:heavy 都会收到同一条消息，
        //     非本组成员只 ACK 不执行，避免 DOCUMENT_INGEST 等被双线程并发跑
        if (task.getType() != null && task.getType().getGroup() != group) {
            log.debug("[任务消费者] 跳过非本组成员任务, taskId={}, type={}, group={}, workerGroup={}",
                    taskId, task.getType(), task.getType().getGroup(), group);
            safeAck(streamId);
            return;
        }

        // 2. 执行前取消检查：取消信号若已存在则直接落 CANCELLED
        if (taskQueueService.isCancelled(taskId)) {
            taskService.markCancelled(taskId, "任务在执行前被用户取消");
            taskQueueService.clearCancel(taskId);
            safeAck(streamId);
            return;
        }

        // 3. 计算本次尝试次数（含本次），用于 markStart 与重试判定
        int prevAttempts = task.getAttempts() == null ? 0 : task.getAttempts();
        int newAttempts = prevAttempts + 1;

        // 4. CAS 抢占 RUNNING：失败说明另一消费者已开始，直接 ACK
        if (!taskService.markStart(taskId, newAttempts, streamId)) {
            log.info("[任务消费者] 抢占失败，跳过重复消费, taskId={}, streamId={}", taskId, streamId);
            safeAck(streamId);
            return;
        }

        // 5. 记录执行线程，便于取消时 interrupt
        runningTasks.put(taskId, Thread.currentThread());
        try {
            log.info("[任务消费者] 开始执行, taskId={}, type={}, attempts={}/{}",
                    taskId, task.getType(), newAttempts,
                    task.getMaxAttempts() == null ? retryPolicyProperties.resolve(task.getType()).getMaxAttempts() : task.getMaxAttempts());

            // 6. 路由到对应执行器
            TaskExecutor executor = getExecutor(task.getType());
            if (executor == null) {
                handleFatal(task, streamId, "不支持的任务类型: " + task.getType());
                return;
            }

            // 7. 执行（注意：executor 内部若检测到取消信号会抛 TaskCancelledException）
            String result = executor.execute(task);

            // 8. 成功
            taskService.markSuccess(taskId, result);
            taskQueueService.clearCancel(taskId);
            safeAck(streamId);

        } catch (TaskCancelledException e) {
            // 用户取消：不重试、不进死信
            taskService.markCancelled(taskId, e.getMessage());
            taskQueueService.clearCancel(taskId);
            safeAck(streamId);
            log.info("[任务消费者] 任务取消, taskId={}", taskId);
        } catch (RetryableTaskException e) {
            handleRetry(task, streamId, newAttempts, e);
        } catch (FatalTaskException e) {
            handleFatal(task, streamId, e.getMessage());
        } catch (Throwable t) {
            // 兜底：未知异常视为 Fatal，避免无限重投
            String error = buildErrorMessage(t);
            handleFatal(task, streamId, error);
        } finally {
            runningTasks.remove(taskId);
        }
    }

    /**
     * 可重试异常路由：判断重试次数是否到顶，到顶则进死信，否则退避延迟后重投主队列
     */
    private void handleRetry(Task task, String streamId, int failedAttempts, RetryableTaskException e) {
        RetryPolicy policy = retryPolicyProperties.resolve(task.getType());
        int maxAttempts = policy.getMaxAttempts();

        if (failedAttempts >= maxAttempts) {
            // 重试次数到顶 → 终态失败 + 死信
            String error = String.format("%s（达到最大尝试次数 %d）", e.getMessage(), maxAttempts);
            handleFatal(task, streamId, error);
            return;
        }

        // 计算退避时长（指数退避）并投递到延迟队列
        long delayMs = policy.computeDelay(failedAttempts - 1);
        long delayAt = System.currentTimeMillis() + delayMs;
        LocalDateTime nextRetryAt = LocalDateTime.now().plusNanos(delayMs * 1_000_000L);

        // 状态置 PENDING_RETRY + 记录 attempts/nextRetryAt
        taskService.markPendingRetry(task.getId(), failedAttempts, nextRetryAt, e.getMessage());

        // ZADD 延迟队列，到点由 TaskDelayScheduler 扫描并 XADD 回主队列
        Task fresh = new Task();
        fresh.setId(task.getId());
        fresh.setType(task.getType());
        fresh.setAttempts(failedAttempts);
        taskQueueService.enqueueDelayed(fresh, delayAt);

        // ACK 旧 streamId（新投递后会生成新 streamId）
        safeAck(streamId);
        log.info("[任务消费者] 任务重试, taskId={}, attempts={}/{}, delayMs={}",
                task.getId(), failedAttempts, maxAttempts, delayMs);
    }

    /**
     * 致命异常路由：直接 markFailed + 死信 Stream
     */
    private void handleFatal(Task task, String streamId, String error) {
        log.error("[任务消费者] 任务失败, taskId={}, error={}", task.getId(), error);
        taskService.markFailed(task.getId(), error);
        taskService.markDeadLetter(task.getId(), error);

        // 死信 Stream 记录原始 streamId 便于回溯
        taskQueueService.sendToDeadLetter(task, streamId, error);
        taskQueueService.clearCancel(task.getId());
        safeAck(streamId);
    }

    /** 安全 ACK：失败仅记录日志，避免影响主流程 */
    private void safeAck(String streamId) {
        if (streamId == null) {
            return;
        }
        try {
            taskQueueService.ack(streamId);
        } catch (Exception e) {
            log.warn("[任务消费者] XACK 失败, streamId={}, error={}", streamId, e.getMessage());
        }
    }

    /** 多实例/多 worker 全局唯一的消费者名（保留：未来若启用 XCLAIM 路由可用） */
    @SuppressWarnings("unused")
    private String generateUniqueConsumerName(TaskType.Group group) {
        String host = System.getenv().getOrDefault("HOSTNAME", "local");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return host + "-" + group.name().toLowerCase() + "-" + suffix;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
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
     * 构建详细的错误信息（对 NPE 等 getMessage 为 null 的异常做特殊处理）
     */
    private String buildErrorMessage(Throwable e) {
        if (ModelErrorClassifier.isFatal(e)) {
            return ModelErrorClassifier.formatDetail(e);
        }
        String msg = e.getMessage();
        if (msg != null && !msg.isBlank()) {
            return msg;
        }
        Throwable cause = e.getCause();
        if (cause != null && cause.getMessage() != null) {
            return e.getClass().getSimpleName() + ": " + cause.getMessage();
        }
        StackTraceElement[] stackTrace = e.getStackTrace();
        if (stackTrace.length > 0) {
            StackTraceElement top = stackTrace[0];
            return e.getClass().getSimpleName() + " at " + top.getClassName() + "." + top.getMethodName()
                    + "(" + top.getFileName() + ":" + top.getLineNumber() + ")";
        }
        return e.getClass().getSimpleName();
    }

    /** 保留供 ReclaimScheduler 反射调用入口（暂未使用，留作扩展点） */
    @SuppressWarnings("unused")
    private List<com.lightbot.task.StaleMessage> listStaleForDebug(TaskType.Group group) {
        return taskQueueService.scanStale(group, Duration.ofMinutes(5), 100);
    }
}

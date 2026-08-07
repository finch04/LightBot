package com.lightbot.config;

import com.lightbot.common.task.FatalTaskException;
import com.lightbot.common.task.RetryableTaskException;
import com.lightbot.common.task.TaskCancelledException;
import com.lightbot.entity.Task;
import com.lightbot.enums.TaskStatus;
import com.lightbot.enums.TaskType;
import com.lightbot.service.TaskService;
import com.lightbot.task.RetryPolicy;
import com.lightbot.task.RetryPolicyProperties;
import com.lightbot.task.TaskExecutor;
import com.lightbot.task.TaskMessage;
import com.lightbot.task.TaskQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务消费链路集成测试（基于 Mockito 编排，不依赖真实 Redis）。
 *
 * <p>验证 Stream 重构后的核心编排逻辑：</p>
 * <ul>
 *   <li>场景1：成功 — execute → markSuccess → ack</li>
 *   <li>场景2：可重试 — RetryableTaskException → markPendingRetry + 延迟入队 → ack</li>
 *   <li>场景3：重试到顶 — RetryableTaskException 已耗尽 → markFailed + 死信</li>
 *   <li>场景4：致命异常 — FatalTaskException → markFailed + 死信</li>
 *   <li>场景5：用户取消 — TaskCancelledException → markCancelled，无死信</li>
 *   <li>场景6：执行前取消 — isCancelled=true → markCancelled</li>
 *   <li>场景7：非活跃任务 — 状态非 PENDING/PENDING_RETRY → ack 跳过</li>
 *   <li>场景8：任务不存在 → ack 跳过</li>
 * </ul>
 *
 * @author finch
 * @since 2026-07-18
 */
@ExtendWith(MockitoExtension.class)
class TaskConsumerConfigIntegrationTest {

    @Mock
    private TaskQueueService taskQueueService;
    @Mock
    private TaskService taskService;
    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private TaskExecutor executor;

    private RetryPolicyProperties retryPolicyProperties;
    private TaskConsumerConfig consumer;

    @BeforeEach
    void setUp() {
        retryPolicyProperties = new RetryPolicyProperties();
        consumer = new TaskConsumerConfig(
                taskQueueService, taskService, applicationContext, retryPolicyProperties);
        // markStart 改为 CAS 返回 boolean，默认抢占成功
        lenient().when(taskService.markStart(anyLong(), anyInt(), anyString())).thenReturn(true);
    }

    private Task newTask(Long id, TaskStatus status, int attempts) {
        Task t = new Task();
        t.setId(id);
        t.setStatus(status);
        t.setAttempts(attempts);
        t.setType(TaskType.DOCUMENT_UPLOAD);
        return t;
    }

    private TaskMessage newMsg(Long taskId, String streamId, int attempts) {
        return new TaskMessage(streamId, taskId, TaskType.DOCUMENT_UPLOAD.name(), attempts);
    }

    // ============ 场景1：成功 ============

    @Test
    void test_handleMessage_whenExecutorSucceeds_shouldMarkSuccessAndAck() throws Exception {
        Task task = newTask(1001L, TaskStatus.PENDING, 0);
        when(taskService.getById(1001L)).thenReturn(task);
        when(applicationContext.getBean("documentUploadExecutor", TaskExecutor.class)).thenReturn(executor);
        when(executor.execute(task)).thenReturn("ok");

        consumer.handleMessage(newMsg(1001L, "s-1", 0), TaskType.Group.DEFAULT);

        // 验证：markStart(attempts=1) → markSuccess → ack
        verify(taskService).markStart(eq(1001L), eq(1), eq("s-1"));
        verify(taskService).markSuccess(eq(1001L), anyString());
        verify(taskQueueService).ack("s-1");
        // 不应触发 retry / dead letter
        verify(taskQueueService, never()).enqueueDelayed(any(), anyLong());
        verify(taskQueueService, never()).sendToDeadLetter(any(), anyString(), anyString());
    }

    // ============ 场景2：可重试异常（仍在重试上限内）============

    @Test
    void test_handleMessage_whenRetryableAndUnderLimit_shouldPendingRetryAndDelayedEnqueue() throws Exception {
        RetryableTaskException ex = new RetryableTaskException("模型超时，可重试");
        Task task = newTask(2002L, TaskStatus.PENDING, 0);
        when(taskService.getById(2002L)).thenReturn(task);
        when(applicationContext.getBean(anyString(), eq(TaskExecutor.class))).thenReturn(executor);
        when(executor.execute(task)).thenThrow(ex);

        // 默认策略：max-attempts=3, backoff-base-ms=5000, multiplier=2.0
        consumer.handleMessage(newMsg(2002L, "s-2", 0), TaskType.Group.DEFAULT);

        // 验证：markPendingRetry(attempts=1) + 延迟入队 + ack
        verify(taskService).markPendingRetry(eq(2002L), eq(1), any(), eq("模型超时，可重试"));
        verify(taskQueueService).enqueueDelayed(any(Task.class), anyLong());
        verify(taskQueueService).ack("s-2");
        // 不应进死信
        verify(taskService, never()).markFailed(anyLong(), anyString());
        verify(taskQueueService, never()).sendToDeadLetter(any(), anyString(), anyString());
    }

    // ============ 场景3：重试到顶 ============

    @Test
    void test_handleMessage_whenRetryableAndExhausted_shouldFatalAndDeadLetter() throws Exception {
        // 调小 max-attempts 让重试立即到顶
        RetryPolicy quickFail = new RetryPolicy();
        quickFail.setMaxAttempts(1);
        retryPolicyProperties.getOverrides().put(TaskType.DOCUMENT_UPLOAD, quickFail);

        RetryableTaskException ex = new RetryableTaskException("模型超时");
        Task task = newTask(3003L, TaskStatus.PENDING_RETRY, 0);
        when(taskService.getById(3003L)).thenReturn(task);
        when(applicationContext.getBean(anyString(), eq(TaskExecutor.class))).thenReturn(executor);
        when(executor.execute(task)).thenThrow(ex);

        // failedAttempts=1, maxAttempts=1 → 立即进死信
        consumer.handleMessage(newMsg(3003L, "s-3", 0), TaskType.Group.DEFAULT);

        // 验证：markFailed + markDeadLetter + sendToDeadLetter + ack（无延迟入队）
        verify(taskService).markFailed(eq(3003L), anyString());
        verify(taskService).markDeadLetter(eq(3003L), anyString());
        verify(taskQueueService).sendToDeadLetter(any(Task.class), eq("s-3"), anyString());
        verify(taskQueueService).ack("s-3");
        verify(taskQueueService, never()).enqueueDelayed(any(), anyLong());
    }

    // ============ 场景4：致命异常 ============

    @Test
    void test_handleMessage_whenFatalException_shouldDirectlyDeadLetter() throws Exception {
        FatalTaskException ex = new FatalTaskException("参数非法，不可重试");
        Task task = newTask(4004L, TaskStatus.PENDING, 0);
        when(taskService.getById(4004L)).thenReturn(task);
        when(applicationContext.getBean(anyString(), eq(TaskExecutor.class))).thenReturn(executor);
        when(executor.execute(task)).thenThrow(ex);

        consumer.handleMessage(newMsg(4004L, "s-4", 0), TaskType.Group.DEFAULT);

        // 验证：直接 markFailed + 死信（不进延迟队列）
        verify(taskService).markFailed(eq(4004L), eq("参数非法，不可重试"));
        verify(taskQueueService).sendToDeadLetter(any(Task.class), eq("s-4"), eq("参数非法，不可重试"));
        verify(taskQueueService).ack("s-4");
        verify(taskQueueService, never()).enqueueDelayed(any(), anyLong());
    }

    // ============ 场景5：用户取消（执行中）============

    @Test
    void test_handleMessage_whenCancelledDuringExecution_shouldMarkCancelledWithoutDeadLetter() throws Exception {
        TaskCancelledException ex = new TaskCancelledException();
        // 任务被拉取时仍为 PENDING；executor 执行过程中检测到取消信号抛 TaskCancelledException
        Task task = newTask(5005L, TaskStatus.PENDING, 0);
        when(taskService.getById(5005L)).thenReturn(task);
        when(applicationContext.getBean(anyString(), eq(TaskExecutor.class))).thenReturn(executor);
        when(executor.execute(task)).thenThrow(ex);

        consumer.handleMessage(newMsg(5005L, "s-5", 1), TaskType.Group.DEFAULT);

        // 验证：markCancelled，不进死信、不重试
        verify(taskService).markCancelled(eq(5005L), any());
        verify(taskQueueService).ack("s-5");
        verify(taskQueueService, never()).sendToDeadLetter(any(), anyString(), anyString());
        verify(taskQueueService, never()).enqueueDelayed(any(), anyLong());
        verify(taskService, never()).markFailed(anyLong(), anyString());
    }

    // ============ 场景6：执行前已取消 ============

    @Test
    void test_handleMessage_whenCancelledBeforeExecution_shouldMarkCancelledAndSkip() {
        Task task = newTask(6006L, TaskStatus.PENDING, 0);
        when(taskService.getById(6006L)).thenReturn(task);
        when(taskQueueService.isCancelled(6006L)).thenReturn(true);

        consumer.handleMessage(newMsg(6006L, "s-6", 0), TaskType.Group.DEFAULT);

        // 验证：markCancelled + clearCancel + ack；不应执行 executor
        verify(taskService).markCancelled(eq(6006L), anyString());
        verify(taskQueueService).clearCancel(6006L);
        verify(taskQueueService).ack("s-6");
        verify(applicationContext, never()).getBean(anyString(), eq(TaskExecutor.class));
    }

    // ============ 场景7：非活跃任务（已完结/已取消）============

    @Test
    void test_handleMessage_whenStatusIsTerminal_shouldAckAndSkip() {
        // 消息重复投递：任务已是 SUCCESS 状态
        Task task = newTask(7007L, TaskStatus.SUCCESS, 1);
        when(taskService.getById(7007L)).thenReturn(task);

        consumer.handleMessage(newMsg(7007L, "s-7", 1), TaskType.Group.DEFAULT);

        // 验证：仅 ack，不应执行 markStart/executor
        verify(taskQueueService).ack("s-7");
        verify(taskService, never()).markStart(anyLong(), anyInt(), anyString());
        verify(applicationContext, never()).getBean(anyString(), eq(TaskExecutor.class));
    }

    // ============ 场景8：任务不存在 ============

    @Test
    void test_handleMessage_whenTaskIsNull_shouldAckAndSkip() {
        when(taskService.getById(8008L)).thenReturn(null);

        consumer.handleMessage(newMsg(8008L, "s-8", 0), TaskType.Group.DEFAULT);

        // 验证：仅 ack（消息被丢弃），不执行任何 mark 操作
        verify(taskQueueService).ack("s-8");
        verify(taskService, never()).markStart(anyLong(), anyInt(), anyString());
        verify(taskService, never()).markFailed(anyLong(), anyString());
    }

    // ============ 场景9：未知异常兜底（防止坏任务无限重投）============

    @Test
    void test_handleMessage_whenUnknownException_shouldTreatAsFatal() throws Exception {
        Task task = newTask(9009L, TaskStatus.PENDING, 0);
        when(taskService.getById(9009L)).thenReturn(task);
        when(applicationContext.getBean(anyString(), eq(TaskExecutor.class))).thenReturn(executor);
        when(executor.execute(task)).thenThrow(new NullPointerException("unexpected"));

        consumer.handleMessage(newMsg(9009L, "s-9", 0), TaskType.Group.DEFAULT);

        // 验证：兜底转 Fatal，避免无限重投
        verify(taskService).markFailed(eq(9009L), anyString());
        verify(taskQueueService).sendToDeadLetter(any(Task.class), eq("s-9"), anyString());
        verify(taskQueueService, never()).enqueueDelayed(any(), anyLong());
    }

    // ============ 场景10：executor 不存在 ============

    @Test
    void test_handleMessage_whenExecutorMissing_shouldDeadLetter() {
        Task task = newTask(1010L, TaskStatus.PENDING, 0);
        when(taskService.getById(1010L)).thenReturn(task);
        when(applicationContext.getBean(anyString(), eq(TaskExecutor.class)))
                .thenThrow(new org.springframework.beans.factory.NoSuchBeanDefinitionException("not found"));

        consumer.handleMessage(newMsg(1010L, "s-10", 0), TaskType.Group.DEFAULT);

        // 验证：handleFatal 走死信流程
        verify(taskService).markFailed(eq(1010L), anyString());
        verify(taskQueueService).sendToDeadLetter(any(Task.class), eq("s-10"), anyString());
        verify(taskQueueService).ack("s-10");
    }
}

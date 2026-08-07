package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.entity.Task;
import com.lightbot.enums.TaskType;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 任务队列服务接口
 *
 * @author finch
 * @since 2026-05-21
 */
public interface TaskService extends IService<Task> {

    /**
     * 创建任务并推入Redis队列
     *
     * @param type    任务类型
     * @param name    任务名称
     * @param userId  用户ID
     * @param refId   关联业务ID
     * @param payload 任务参数(JSON)
     * @return 创建的任务
     */
    Task createTask(TaskType type, String name, Long userId, Long refId, String payload);

    /**
     * 更新任务进度（同步写 DB + Redis Hash，前端可从 Hash 毫秒级读取）
     */
    void updateProgress(Long taskId, int progress, String message);

    /**
     * 标记任务为执行中（旧版保留兼容；新流程请用 {@link #markStart}）
     */
    void markRunning(Long taskId);

    /**
     * 标记任务开始执行（Stream 模式）：status=RUNNING + 记录 attempts/streamId
     * <p>CAS：仅当当前状态为 PENDING / PENDING_RETRY 时更新成功，用于防止双消费组并发抢占</p>
     *
     * @param taskId   任务ID
     * @param attempts 本次执行的累计尝试次数（含本次）
     * @param streamId 主 Stream 消息 ID
     * @return true 表示抢占成功并已置为 RUNNING；false 表示已被其他消费者抢占
     */
    boolean markStart(Long taskId, int attempts, String streamId);

    /**
     * 标记任务为等待重试：status=PENDING_RETRY + 记录 attempts/nextRetryAt + 拼接错误信息
     *
     * @param taskId      任务ID
     * @param attempts    本次失败的累计尝试次数
     * @param nextRetryAt 下次重试时间
     * @param error       失败原因
     */
    void markPendingRetry(Long taskId, int attempts, LocalDateTime nextRetryAt, String error);

    /**
     * 标记任务成功
     */
    void markSuccess(Long taskId, String result);

    /**
     * 标记任务失败
     */
    void markFailed(Long taskId, String error);

    /**
     * 标记任务进入死信：dead_letter=1
     */
    void markDeadLetter(Long taskId, String error);

    /**
     * 请求取消任务，返回是否成功
     */
    boolean requestCancel(Long taskId);

    /**
     * 标记任务为已取消
     *
     * @param taskId  任务ID
     * @param message 取消说明
     */
    void markCancelled(Long taskId, String message);

    /**
     * 分页查询企业任务（userId 传 null 表示全企业）
     */
    Page<Task> listByUserId(Long userId, int pageNum, int pageSize, String name, String status, String type);

    /**
     * 获取任务详情（企业共享；userId 仅兼容旧签名，不再做归属校验）
     */
    Task getTaskById(Long taskId, Long userId);

    /**
     * 统计指定状态的任务数量（userId 传 null 表示全企业）
     */
    Long countByStatus(Long userId, String status);

    /**
     * 按类型统计进行中任务数量（userId 传 null 表示全企业）
     */
    Map<String, Long> countByType(Long userId);

    /**
     * 删除任务（仅已终态；企业共享，建设者可删）
     */
    void deleteTask(Long taskId, Long userId);
}

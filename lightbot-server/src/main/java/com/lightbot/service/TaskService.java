package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.entity.Task;
import com.lightbot.enums.TaskType;

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
     * 更新任务进度
     */
    void updateProgress(Long taskId, int progress, String message);

    /**
     * 标记任务为执行中
     */
    void markRunning(Long taskId);

    /**
     * 标记任务成功
     */
    void markSuccess(Long taskId, String result);

    /**
     * 标记任务失败
     */
    void markFailed(Long taskId, String error);

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
     * 分页查询用户任务
     */
    Page<Task> listByUserId(Long userId, int pageNum, int pageSize, String name, String status, String type);

    /**
     * 获取任务详情（校验用户归属）
     */
    Task getTaskById(Long taskId, Long userId);

    /**
     * 统计用户指定状态的任务数量
     *
     * @param userId 用户ID
     * @param status 任务状态
     * @return 任务数量
     */
    Long countByStatus(Long userId, String status);

    /**
     * 按类型统计用户任务数量（仅统计进行中+等待中的任务）
     *
     * @param userId 用户ID
     * @return 类型 -> 数量
     */
    Map<String, Long> countByType(Long userId);

    /**
     * 删除任务（仅已终态任务可删除）
     *
     * @param taskId 任务ID
     * @param userId 用户ID（校验归属）
     */
    void deleteTask(Long taskId, Long userId);
}

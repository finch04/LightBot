package com.lightbot.service.port;

/**
 * 任务计数变更通知端口
 * <p>TaskService 在任务创建/状态变更时通过此端口通知上层推送最新计数，
 * 由 server 层的 SSE 控制器实现，避免下层 service 反向依赖 controller</p>
 *
 * @author finch
 */
public interface TaskCountNotifier {

    /**
     * 通知指定用户的任务计数已变更（兼容旧调用）
     *
     * @param userId 用户ID
     */
    void notifyUser(Long userId);

    /**
     * 通知所有在线建设者：企业任务计数已变更
     */
    default void notifyAllUsers() {
        notifyUser(null);
    }
}

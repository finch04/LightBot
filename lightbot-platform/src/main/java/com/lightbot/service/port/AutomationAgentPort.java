package com.lightbot.service.port;

/**
 * 自动化任务调用智能体端口，由 agent 模块实现。
 * <p>执行路径必须与 UI 对话一致（含工作流 Agent）。
 *
 * @author finch
 * @since 2026-07-26
 */
public interface AutomationAgentPort {

    /**
     * 校验智能体归属并返回名称
     *
     * @param agentId 智能体 ID
     * @param userId  用户 ID
     * @return 智能体名称
     */
    String requireAgentName(Long agentId, Long userId);

    /**
     * 以指定用户身份执行一轮对话（与页面 chatStream 同链路）
     *
     * @param userId       用户 ID
     * @param agentId      智能体 ID
     * @param instruction  文字指令
     * @param sessionTitle 会话标题（任务名）
     * @return 执行结果（含详情快照）
     */
    AutomationAgentRunResult run(Long userId, Long agentId, String instruction, String sessionTitle);

    /**
     * 自动化对话执行结果
     *
     * @param sessionId 会话 ID
     * @param summary   回复摘要（正文）
     * @param detailJson 与对话消息同构的详情 JSON（content/metadata/toolEvents）
     */
    record AutomationAgentRunResult(Long sessionId, String summary, String detailJson) {
    }
}

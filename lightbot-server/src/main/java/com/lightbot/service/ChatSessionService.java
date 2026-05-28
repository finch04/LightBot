package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.entity.ChatSession;

/**
 * 对话会话服务接口
 *
 * @author finch
 * @since 2026-05-19
 */
public interface ChatSessionService extends IService<ChatSession> {

    /**
     * 创建新会话
     *
     * @param agentId AgentID
     * @return 会话
     */
    ChatSession createSession(Long agentId);

    /**
     * 分页查询当前用户的会话列表
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 分页结果
     */
    Page<ChatSession> listMySessions(int pageNum, int pageSize);

    /**
     * 更新会话标题
     *
     * @param sessionId 会话ID
     * @param title     新标题
     */
    void updateTitle(Long sessionId, String title);

    /**
     * 归档会话
     *
     * @param sessionId 会话ID
     */
    void archiveSession(Long sessionId);

    /**
     * 物理删除会话及其所有消息
     *
     * @param sessionId 会话ID
     */
    void deleteSession(Long sessionId);

    /**
     * 切换会话置顶状态
     *
     * @param sessionId 会话ID
     */
    void togglePin(Long sessionId);

    /**
     * 更新会话统计（消息数、token数、最后消息时间）
     *
     * @param sessionId  会话ID
     * @param tokenCount 本次token消耗
     */
    void updateStats(Long sessionId, int tokenCount);

    /**
     * 对话中切换智能体后，将会话绑定到新的 Agent
     *
     * @param sessionId 会话ID
     * @param agentId   新 Agent ID
     */
    void updateAgentId(Long sessionId, Long agentId);
}

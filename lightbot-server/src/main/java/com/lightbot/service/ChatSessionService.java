package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.entity.ChatSession;

import java.util.List;

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
     * 分页查询当前用户的会话列表（支持关键词搜索）
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @param keyword  标题模糊搜索关键词（可为null）
     * @return 分页结果
     */
    Page<ChatSession> listMySessions(int pageNum, int pageSize, String keyword);

    /**
     * 批量删除会话（物理删除，包含所有消息）
     *
     * @param ids 会话ID列表
     */
    void deleteSessions(List<Long> ids);

    /**
     * 获取会话标题（轻量查询，跳过缓存）
     *
     * @param sessionId 会话ID
     * @return 标题，不存在返回null
     */
    String getTitle(Long sessionId);

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
     * 对话中切换智能体或版本后，将会话绑定到新的 Agent 和版本快照
     *
     * @param sessionId      会话ID
     * @param agentId        新 Agent ID
     * @param agentVersionId Agent版本快照ID（agent_version.id），null=未指定
     */
    void updateSessionAgent(Long sessionId, Long agentId, Long agentVersionId);

    /**
     * 删除指定 Agent 的所有会话（级联删除消息和轨迹，跳过权限校验）
     *
     * @param agentId AgentID
     */
    void deleteByAgentId(Long agentId);
}

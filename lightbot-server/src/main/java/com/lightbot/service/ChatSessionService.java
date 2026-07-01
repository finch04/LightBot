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
     * @param userId  用户ID
     * @param agentId AgentID
     * @return 会话
     */
    ChatSession createSession(Long userId, Long agentId);

    /**
     * 分页查询当前用户的会话列表
     *
     * @param userId   用户ID
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 分页结果
     */
    Page<ChatSession> listMySessions(Long userId, int pageNum, int pageSize);

    /**
     * 分页查询当前用户的会话列表（支持关键词搜索）
     *
     * @param userId   用户ID
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @param keyword  标题模糊搜索关键词（可为null）
     * @return 分页结果
     */
    Page<ChatSession> listMySessions(Long userId, int pageNum, int pageSize, String keyword);

    /**
     * 批量删除会话（物理删除，包含所有消息）
     *
     * @param userId 用户ID
     * @param ids    会话ID列表
     */
    void deleteSessions(Long userId, List<Long> ids);

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

    /**
     * 导出会话为 Markdown 或 JSON 格式
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @param format    格式：markdown 或 json
     * @return 文件内容字符串
     */
    String exportSession(Long userId, Long sessionId, String format);

    /**
     * 批量追加用户上传附件到会话 attachments 索引
     */
    void appendSessionAttachments(Long sessionId, List<com.lightbot.dto.ChatAttachmentDTO> attachments, String source);

    /**
     * 注册会话附件（AI 工具产出等，按 objectKey 去重）
     */
    void registerSessionAttachments(Long sessionId, List<com.lightbot.dto.SessionAttachmentVO> attachments);

    /**
     * 获取会话附件列表
     */
    List<com.lightbot.dto.SessionAttachmentVO> getSessionAttachments(Long sessionId);

    /**
     * 校验会话归属当前用户，不属于抛 SESSION_NOT_FOUND。
     *
     * @param sessionId 会话 ID
     * @param userId    当前用户 ID
     */
    void ensureOwnedByUser(Long sessionId, Long userId);

    /**
     * 按 objectKey 移除会话附件索引条目（删除 MinIO 文件后同步用）。
     *
     * @param sessionId 会话 ID
     * @param objectKey MinIO objectKey
     */
    void removeSessionAttachmentByObjectKey(Long sessionId, String objectKey);

    /**
     * 从会话附件列表中移除一个附件
     */
    void removeSessionAttachment(Long sessionId, String attachmentId);
}

package com.lightbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.MemoryExtractDTO;
import com.lightbot.dto.MemoryScope;
import com.lightbot.dto.UserMemoryRequestDTO;
import com.lightbot.entity.UserMemory;
import com.lightbot.vo.ExternalMemoryUserSummaryVO;
import com.lightbot.vo.UserMemoryVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用户长期记忆服务
 *
 * @author finch
 * @since 2026-07-09
 */
public interface UserMemoryService extends IService<UserMemory> {

    List<UserMemoryVO> listCurrentUserMemories(String keyword, String status);

    UserMemoryVO createCurrentUserMemory(UserMemoryRequestDTO request);

    UserMemoryVO updateCurrentUserMemory(Long id, UserMemoryRequestDTO request);

    void deleteCurrentUserMemory(Long id);

    UserMemoryVO updateCurrentUserMemoryStatus(Long id, String status);

    /**
     * 工具保存记忆（平台用户）
     */
    UserMemoryVO saveFromTool(Long userId, Long agentId, Long sessionId, Long sourceMessageId,
                              String memoryType, String content, List<String> keywords, BigDecimal confidence);

    /**
     * 工具保存记忆（支持开放 API 外部用户命名空间）
     */
    UserMemoryVO saveFromTool(MemoryScope scope, Long agentId, Long sessionId, Long sourceMessageId,
                              String memoryType, String content, List<String> keywords, BigDecimal confidence);

    List<UserMemory> searchForPrompt(Long userId, Long agentId, String query, int limit);

    List<UserMemory> searchForPrompt(MemoryScope scope, Long agentId, String query, int limit);

    String buildMemoryPrompt(Long userId, Long agentId, String query, int limit);

    String buildMemoryPrompt(MemoryScope scope, Long agentId, String query, int limit);

    void extractAsync(MemoryExtractDTO request);

    /**
     * 停用指定命名空间下的记忆（工具删除）
     */
    boolean disableMemory(MemoryScope scope, Long memoryId);

    /**
     * 管理员：汇总某企业 API Key 下各外部用户的记忆数量
     *
     * @param apiKeyId 企业 API Key ID
     * @return 外部用户记忆汇总
     */
    List<ExternalMemoryUserSummaryVO> listExternalUserSummaries(Long apiKeyId);

    /**
     * 管理员：查询某 Key 下外部用户记忆明细
     *
     * @param apiKeyId       企业 API Key ID
     * @param externalUserId 外部用户 ID（可选，空则返回该 Key 下全部）
     * @param status         状态过滤（可选）
     * @param keyword        内容关键词（可选）
     * @return 记忆列表
     */
    List<UserMemoryVO> listExternalMemories(Long apiKeyId, String externalUserId, String status, String keyword);

    /**
     * 管理员：清空某 Key 下指定外部用户的全部记忆
     *
     * @param apiKeyId       企业 API Key ID
     * @param externalUserId 外部用户 ID
     * @return 删除条数
     */
    int clearExternalUserMemories(Long apiKeyId, String externalUserId);

    /**
     * 管理员：删除单条外部用户记忆
     *
     * @param apiKeyId 企业 API Key ID
     * @param memoryId 记忆 ID
     */
    void deleteExternalMemory(Long apiKeyId, Long memoryId);
}

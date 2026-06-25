package com.lightbot.service;

import com.lightbot.dto.WorkflowGraphDTO;
import com.lightbot.dto.WorkflowVersionVO;
import com.lightbot.entity.Agent;
import com.lightbot.workflow.WorkflowDefinition;

import java.util.List;
import java.util.Map;

/**
 * Agent 版本管理：草稿、发布、历史版本查询
 */
public interface AgentVersionService {

    /**
     * 获取工作流编辑态（草稿图 + 发布图 + 状态）
     */
    Map<String, Object> getWorkflowEditorState(Long agentId);

    void saveWorkflowDraft(Long agentId, WorkflowGraphDTO graph);

    Map<String, Object> publishWorkflow(Long agentId, WorkflowGraphDTO graph);

    List<WorkflowVersionVO> listPublishedVersions(Long agentId);

    Map<String, Object> getPublishedVersionGraph(Long agentId, Integer version);

    /**
     * 获取已发布版本详情（对话型返回 payload，工作流型返回 graph）
     */
    Map<String, Object> getPublishedVersionDetail(Long agentId, Integer version);

    void restorePublishedToDraft(Long agentId, Integer version);

    /**
     * 删除已发布版本（逻辑删除）
     * 注意：当前线上版本不允许删除
     */
    void deletePublishedVersion(Long agentId, Integer version);

    /**
     * 对话型 Agent 发布（快照当前配置）
     */
    Map<String, Object> publishChatAgent(Long agentId, String description);

    /**
     * 保存对话型草稿快照（编辑后暂存）
     */
    void saveChatDraft(Long agentId);

    /**
     * 运行时加载已发布配置（对话），无则返回 null
     */
    Map<String, Object> loadPublishedRuntimeConfig(Long agentId);

    /**
     * 加载指定版本的完整 payload（含绑定 ID），供对话运行时提取工具/知识库等绑定关系
     *
     * @param agentId Agent ID
     * @param configVersion 0=草稿，&gt;0=指定发布版本
     * @return payload Map（含 toolIds/knowledgeIds 等），无数据返回 null
     */
    Map<String, Object> loadVersionPayload(Long agentId, Integer configVersion);

    /**
     * 按版本快照主键加载 payload（含绑定 ID）
     * <p>用于会话恢复场景：session 存储 agent_version.id，按 ID 精确加载，避免版本编号复用导致误匹配</p>
     *
     * @param versionId agent_version.id
     * @return payload Map（含 toolIds/knowledgeIds 等），版本不存在或已删除返回 null
     */
    Map<String, Object> loadVersionPayloadById(Long versionId);

    /**
     * 获取草稿版本的主键 ID
     *
     * @param agentId Agent ID
     * @return 草稿行的 agent_version.id，无草稿返回 null
     */
    Long getDraftVersionId(Long agentId);

    WorkflowDefinition loadWorkflowDefinition(Long agentId, boolean useDraft);

    /**
     * 对话运行时按版本加载配置（会同步 systemPrompt 等字段到 agent）
     *
     * @param configVersion null=默认；0=草稿；&gt;0=指定发布版本
     */
    Map<String, Object> resolveRuntimeForChat(Agent agent, Integer configVersion);

    /**
     * 对话运行时按版本加载工作流定义
     */
    WorkflowDefinition loadWorkflowDefinitionForChat(Long agentId, Integer configVersion);

    /**
     * Agent 创建后初始化草稿版本行
     */
    void initDraftOnCreate(Agent agent);

    /**
     * Agent 创建后使用预定义工作流快照初始化草稿版本行
     *
     * @param agent             Agent 实体
     * @param workflowSnapshot  工作流快照（含 kind + graph）
     */
    void initDraftWithWorkflow(Agent agent, Map<String, Object> workflowSnapshot);

    /**
     * 从 agent.config 迁移历史版本数据（一次性）
     */
    void migrateLegacyIfNeeded(Agent agent);

    /**
     * 删除指定 Agent 的所有版本记录（级联删除，跳过权限校验）
     *
     * @param agentId AgentID
     */
    void deleteByAgentId(Long agentId);
}

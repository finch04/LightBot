package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.AgentChatCapabilitiesDTO;
import com.lightbot.dto.AgentSaveDTO;
import com.lightbot.vo.MentionOptionsVO;
import com.lightbot.entity.Agent;
import com.lightbot.entity.McpServer;
import com.lightbot.entity.Tool;
import org.springframework.web.multipart.MultipartFile;

import com.lightbot.vo.WorkflowExampleVO;

import java.util.List;
import java.util.Map;

/**
 * Agent服务接口
 *
 * @author finch
 * @since 2026-05-19
 */
public interface AgentService extends IService<Agent> {

    /**
     * 创建Agent
     *
     * @param request Agent创建请求
     * @return Agent
     */
    Agent create(AgentSaveDTO request);

    /**
     * 更新Agent
     *
     * @param request Agent更新请求
     * @return Agent
     */
    Agent update(AgentSaveDTO request);

    /**
     * 分页查询当前用户的Agent列表
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 分页结果
     */
    Page<Agent> listMyAgents(int pageNum, int pageSize, String name, String agentType, boolean includeDefault);

    /**
     * 查询用户的默认Agent
     *
     * @param userId 用户ID
     * @return 默认Agent，不存在返回null
     */
    Agent getDefaultAgent(long userId);

    /**
     * 设置指定Agent为用户的默认Agent（同时清除该用户其他默认标记）
     *
     * @param agentId Agent ID
     */
    void setDefaultAgent(long agentId);

    /**
     * 获取 Agent 详情（包含绑定的知识库 ID 列表）
     *
     * @param id Agent ID
     * @return Agent 信息和绑定的知识库 ID 列表
     */
    Map<String, Object> getAgentDetail(Long id);

    /**
     * 按对话配置版本解析 Agent 对话能力（上传/语音/TTS 等）
     *
     * @param id            Agent ID
     * @param configVersion null 或省略=与线上一致；0=暂存草稿；&gt;0=指定发布版本
     */
    AgentChatCapabilitiesDTO getChatCapabilities(Long id, Integer configVersion);

    /**
     * 删除Agent（逻辑删除）
     *
     * @param id 主键ID
     */
    void deleteById(Long id);

    /**
     * AI生成系统提示词
     *
     * @param id Agent ID
     * @return 生成的系统提示词
     */
    String generateSystemPrompt(Long id);

    /**
     * AI生成推荐问题
     *
     * @param id Agent ID
     * @return 推荐问题列表（JSON数组字符串）
     */
    String generateRecommendedQuestions(Long id);

    /**
     * 上传Agent头像到MinIO，返回头像URL
     *
     * @param id   Agent ID
     * @param file 头像文件
     * @return 头像访问URL
     */
    String uploadAvatar(Long id, MultipartFile file);

    /**
     * 获取 Agent 绑定的知识库 ID 列表
     *
     * @param agentId Agent ID
     * @return 知识库 ID 列表
     */
    List<Long> getKnowledgeIds(Long agentId);

    /**
     * 更新 Agent 的知识库绑定
     *
     * @param agentId      Agent ID
     * @param knowledgeIds 知识库 ID 列表
     */
    void updateKnowledgeBindings(Long agentId, List<Long> knowledgeIds);

    /**
     * 获取 Agent 绑定的问数数据集 ID 列表（兼容旧配置）
     *
     * @param agentId Agent ID
     * @return 数据集 ID 列表
     */
    List<Long> getDatasetIds(Long agentId);

    /**
     * 更新 Agent 的问数数据集绑定（兼容旧配置）
     *
     * @param agentId    Agent ID
     * @param datasetIds 问数数据集 ID 列表
     */
    void updateDatasetBindings(Long agentId, List<Long> datasetIds);

    /**
     * 获取 Agent 可问数据模型 ID 列表（由已绑分类展开；兼容旧版直接绑模型）
     *
     * @param agentId Agent ID
     * @return 数据模型 ID 列表
     */
    List<Long> getDataModelIds(Long agentId);

    /**
     * 获取 Agent 可问数据分类 ID 列表（主路径绑定维度）
     *
     * @param agentId Agent ID
     * @return 数据模型分类 ID 列表
     */
    List<Long> getDataModelCategoryIds(Long agentId);

    /**
     * 更新可问数据分类绑定；该类下全部模型即可问，并自动 ensure 语义配置
     *
     * @param agentId     Agent ID
     * @param categoryIds 数据模型分类 ID 列表
     */
    void updateDataModelCategoryBindings(Long agentId, List<Long> categoryIds);

    /**
     * 更新可问数据模型绑定（兼容旧接口）；会清空分类绑定
     *
     * @param agentId      Agent ID
     * @param dataModelIds 数据模型 ID 列表
     */
    void updateDataModelBindings(Long agentId, List<Long> dataModelIds);

    /**
     * 获取 Agent 绑定的工具ID列表
     *
     * @param agentId Agent ID
     * @return 工具ID列表
     */
    List<Long> getToolIds(Long agentId);

    /**
     * 更新 Agent 的工具绑定
     *
     * @param agentId  Agent ID
     * @param toolIds 工具ID列表
     */
    void updateToolBindings(Long agentId, List<Long> toolIds);

    /**
     * 获取 Agent 绑定的工具详情列表
     *
     * @param agentId Agent ID
     * @return 工具详情列表
     */
    List<Tool> getToolDetails(Long agentId);

    /**
     * 获取 Agent 绑定的 MCP Server ID 列表
     *
     * @param agentId Agent ID
     * @return MCP Server ID 列表
     */
    List<Long> getMcpServerIds(Long agentId);

    /**
     * 更新 Agent 的 MCP Server 绑定
     *
     * @param agentId      Agent ID
     * @param mcpServerIds MCP Server ID 列表
     */
    void updateMcpServerBindings(Long agentId, List<Long> mcpServerIds);

    /**
     * 获取 Agent 绑定的 MCP Server 详情列表
     *
     * @param agentId Agent ID
     * @return MCP Server 详情列表
     */
    List<McpServer> getMcpServerDetails(Long agentId);

    /**
     * 获取 Agent 绑定的 SubAgent ID 列表
     *
     * @param agentId Agent ID
     * @return SubAgent ID 列表
     */
    List<Long> getSubAgentIds(Long agentId);

    /**
     * 更新 Agent 的 SubAgent 绑定
     *
     * @param agentId     Agent ID
     * @param subAgentIds SubAgent ID 列表
     */
    void updateSubAgentBindings(Long agentId, List<Long> subAgentIds);

    /**
     * 获取 Agent 绑定的 Skill ID 列表
     *
     * @param agentId Agent ID
     * @return Skill ID 列表
     */
    List<Long> getSkillIds(Long agentId);

    /**
     * 更新 Agent 的 Skill 绑定
     *
     * @param agentId  Agent ID
     * @param skillIds Skill ID 列表
     */
    void updateSkillBindings(Long agentId, List<Long> skillIds);

    /**
     * 获取内置示例工作流列表
     *
     * @return 示例列表
     */
    List<WorkflowExampleVO> listWorkflowExamples();

    /**
     * 根据示例 key 创建工作流 Agent
     *
     * @param key 示例标识
     * @return 创建的 Agent
     */
    Agent createFromWorkflowExample(String key);

    /**
     * 克隆Agent（深拷贝配置+绑定关系，名称加"(副本)"后缀）
     *
     * @param id 源Agent ID
     * @return 克隆后的新Agent
     */
    Agent clone(Long id);

    /**
     * 按用户ID查询Agent列表（管理员用）
     *
     * @param userId 用户ID
     * @return Agent列表
     */
    /**
     * 按创建人查询 Agent（管理员审计用，非访问控制）
     */
    List<Agent> listByUserId(Long userId);

    /**
     * 获取 Agent 当前可用的 mention 候选资源（按版本快照或当前绑定聚合）
     *
     * @param agentId        Agent ID
     * @param agentVersionId Agent 版本快照 ID，为空则按当前激活版本/暂存
     * @param types          限定返回的资源类型（逗号分隔），为空返回全部一期类型
     * @return 分组候选资源
     */
    MentionOptionsVO getMentionOptions(Long agentId, Long agentVersionId, String types);
}

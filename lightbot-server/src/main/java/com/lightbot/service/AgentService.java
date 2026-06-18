package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.AgentChatCapabilitiesDTO;
import com.lightbot.entity.Agent;
import com.lightbot.entity.McpServer;
import com.lightbot.entity.Tool;
import org.springframework.web.multipart.MultipartFile;

import com.lightbot.dto.WorkflowExampleVO;

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
     * @param agent Agent信息
     * @return Agent
     */
    Agent create(Agent agent);

    /**
     * 更新Agent
     *
     * @param agent Agent信息
     * @return Agent
     */
    Agent update(Agent agent);

    /**
     * 分页查询当前用户的Agent列表
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 分页结果
     */
    Page<Agent> listMyAgents(int pageNum, int pageSize, String name, String agentType);

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
     * 按用户ID查询Agent列表（管理员用）
     *
     * @param userId 用户ID
     * @return Agent列表
     */
    List<Agent> listByUserId(Long userId);
}

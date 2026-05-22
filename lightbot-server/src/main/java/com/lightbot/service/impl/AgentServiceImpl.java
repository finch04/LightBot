package com.lightbot.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.entity.Agent;
import com.lightbot.enums.AgentStatus;
import com.lightbot.enums.ErrorCode;
import org.springframework.util.StringUtils;
import com.lightbot.mapper.AgentMapper;
import com.lightbot.model.ModelFactory;
import com.lightbot.service.AgentKnowledgeService;
import com.lightbot.service.AgentService;
import com.lightbot.util.MinioUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent服务实现类
 *
 * @author finch
 * @since 2026-05-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl extends ServiceImpl<AgentMapper, Agent>
        implements AgentService {

    private final AgentKnowledgeService agentKnowledgeService;
    private final ModelFactory modelFactory;
    private final ObjectMapper objectMapper;
    private final MinioUtil minioUtil;

    private static final String GENERATE_PROMPT_SYSTEM = """
            你是一个AI助手提示词生成专家。根据用户提供的Agent名称和描述，生成一段专业的系统提示词。
            要求：
            - 提示词应定义Agent的角色、能力范围和行为规范
            - 语言简洁专业，不超过500字
            - 直接输出提示词内容，不要加任何前缀或解释
            """;

    private static final String GENERATE_QUESTIONS_SYSTEM = """
            你是一个推荐问题生成助手。根据用户提供的Agent名称和描述，生成3个推荐问题。
            严格规则：
            - 只输出JSON数组，不要任何其他文字
            - 恰好3个问题，每个问题不超过30个字
            - 问题应与Agent的功能和领域相关
            - 格式：["问题1","问题2","问题3"]
            """;

    @Override
    public Agent create(Agent agent) {
        // 1. 获取当前用户ID
        long userId = StpUtil.getLoginIdAsLong();

        // 2. 初始化Agent字段
        agent.setUserId(userId);
        agent.setStatus(AgentStatus.DRAFT);
        agent.setVersion(1);
        save(agent);
        return agent;
    }

    @Override
    public Agent update(Agent agent) {
        // 1. 校验存在性
        Agent existing = getById(agent.getId());
        if (existing == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND);
        }

        // 2. 更新允许修改的字段
        existing.setName(agent.getName());
        existing.setDescription(agent.getDescription());
        existing.setSystemPrompt(agent.getSystemPrompt());
        existing.setWelcomeMessage(agent.getWelcomeMessage());
        existing.setRecommendedQuestions(agent.getRecommendedQuestions());
        existing.setAvatar(agent.getAvatar());
        existing.setIcon(agent.getIcon());
        existing.setAgentType(agent.getAgentType());
        existing.setConfig(agent.getConfig());
        updateById(existing);
        return existing;
    }

    @Override
    public Page<Agent> listMyAgents(int pageNum, int pageSize, String name) {
        long userId = StpUtil.getLoginIdAsLong();
        return page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Agent>()
                        .eq(Agent::getUserId, userId)
                        .like(StringUtils.hasText(name), Agent::getName, name)
                        .orderByDesc(Agent::getCreateTime));
    }

    @Override
    public Map<String, Object> getAgentDetail(Long id) {
        // 1. 获取 Agent 信息
        Agent agent = getById(id);
        if (agent == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND);
        }

        // 2. 获取绑定的知识库 ID 列表（转为字符串避免前端 Long 精度丢失）
        List<Long> knowledgeIds = agentKnowledgeService.getKnowledgeIds(id);
        List<String> knowledgeIdStrs = knowledgeIds.stream()
                .map(String::valueOf)
                .toList();

        // 3. 组装返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("agent", agent);
        result.put("knowledgeIds", knowledgeIdStrs);
        return result;
    }

    @Override
    public void deleteById(Long id) {
        Agent agent = getById(id);
        if (agent == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND);
        }
        removeById(id);
    }

    @Override
    public String generateSystemPrompt(Long id) {
        // 1. 校验Agent存在性
        Agent agent = getById(id);
        if (agent == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND);
        }

        // 2. 构建提示词
        String userMessage = String.format("Agent名称：%s\nAgent描述：%s",
                agent.getName(),
                agent.getDescription() != null ? agent.getDescription() : "暂无描述");

        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(GENERATE_PROMPT_SYSTEM));
        messages.add(new UserMessage(userMessage));

        // 3. 调用AI生成
        Long providerId = resolveProviderId();
        ChatModel chatModel = modelFactory.getChatModel(providerId);
        ChatResponse response = chatModel.call(new Prompt(messages));
        String result = response.getResult().getOutput().getText().trim();

        log.info("[Agent] AI生成系统提示词: agentId={}", id);
        return result;
    }

    @Override
    public String generateRecommendedQuestions(Long id) {
        // 1. 校验Agent存在性
        Agent agent = getById(id);
        if (agent == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND);
        }

        // 2. 构建提示词
        String userMessage = String.format("Agent名称：%s\nAgent描述：%s",
                agent.getName(),
                agent.getDescription() != null ? agent.getDescription() : "暂无描述");

        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(GENERATE_QUESTIONS_SYSTEM));
        messages.add(new UserMessage(userMessage));

        // 3. 调用AI生成
        Long providerId = resolveProviderId();
        ChatModel chatModel = modelFactory.getChatModel(providerId);
        ChatResponse response = chatModel.call(new Prompt(messages));
        String json = response.getResult().getOutput().getText().trim();

        // 4. 清理可能的markdown代码块标记
        json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();

        // 5. 验证JSON格式
        try {
            List<String> questions = objectMapper.readValue(json, new TypeReference<>() {});
            // 验证约束：最多3个，每个不超过30字
            if (questions.size() > 3) {
                questions = questions.subList(0, 3);
            }
            json = objectMapper.writeValueAsString(questions);
        } catch (Exception e) {
            log.warn("[Agent] 推荐问题JSON解析失败: {}", e.getMessage());
            throw new BizException(ErrorCode.AI_GENERATE_FAILED);
        }

        log.info("[Agent] AI生成推荐问题: agentId={}", id);
        return json;
    }

    @Override
    public String uploadAvatar(Long id, MultipartFile file) {
        // 1. 校验Agent存在性
        Agent agent = getById(id);
        if (agent == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND);
        }

        // 2. 生成存储路径：agent/{agentId}/avatar/{uuid}.{ext}
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.lastIndexOf('.') > 0) {
            ext = originalName.substring(originalName.lastIndexOf('.'));
        }
        String filePath = String.format("agent/%d/avatar/%s%s", id, UUID.randomUUID().toString().replace("-", ""), ext);

        // 3. 删除旧头像（如果有）
        if (agent.getAvatar() != null && !agent.getAvatar().isEmpty()) {
            minioUtil.delete(agent.getAvatar());
        }

        // 4. 上传新头像
        minioUtil.upload(file, filePath);

        // 5. 更新Agent的avatar字段
        agent.setAvatar(filePath);
        updateById(agent);

        log.info("[Agent] 头像上传成功: agentId={}, path={}", id, filePath);
        return filePath;
    }

    @Override
    public Agent getDefaultAgent(long userId) {
        return getOne(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getUserId, userId)
                .eq(Agent::getIsDefault, true)
                .last("LIMIT 1"));
    }

    @Override
    public void setDefaultAgent(long agentId) {
        // 1. 校验Agent存在性
        Agent agent = getById(agentId);
        if (agent == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND);
        }

        // 2. 清除该用户其他Agent的默认标记
        long userId = agent.getUserId();
        List<Agent> currentDefaults = list(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getUserId, userId)
                .eq(Agent::getIsDefault, true));
        for (Agent defaultAgent : currentDefaults) {
            defaultAgent.setIsDefault(false);
            updateById(defaultAgent);
        }

        // 3. 设置当前Agent为默认
        agent.setIsDefault(true);
        updateById(agent);
    }

    /**
     * 解析providerId（优先使用Agent配置中的，否则使用第一个可用的）
     */
    private Long resolveProviderId() {
        List<Long> providerIds = modelFactory.getAvailableProviderIds();
        if (providerIds.isEmpty()) {
            throw new BizException(ErrorCode.AI_NO_PROVIDER);
        }
        return providerIds.get(0);
    }
}

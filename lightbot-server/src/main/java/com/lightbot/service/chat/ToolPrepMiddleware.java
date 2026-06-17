package com.lightbot.service.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lightbot.constant.ConfigKeys;
import com.lightbot.entity.Agent;
import com.lightbot.entity.Tool;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.ModelProviderType;
import com.lightbot.enums.ToolType;
import com.lightbot.model.ModelFactory;
import com.lightbot.entity.ModelProvider;
import com.lightbot.service.ModelProviderService;
import com.lightbot.service.AgentService;
import com.lightbot.service.McpClientService;
import com.lightbot.service.ToolService;
import com.lightbot.subagent.DelegateSubAgentTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工具准备中间件：构建 ChatOptions（含工具回调）、提取工具映射
 *
 * @author finch
 * @since 2026-05-23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolPrepMiddleware implements ChatMiddleware {

    private final ModelFactory modelFactory;
    private final AgentService agentService;
    private final ToolService toolService;
    private final McpClientService mcpClientService;
    private final ModelProviderService modelProviderService;
    private final DelegateSubAgentTool delegateSubAgentTool;

    @Override
    public Flux<String> execute(ChatContext ctx, ChatMiddlewareChain next) {
        prepare(ctx);
        return next.proceed(ctx);
    }

    /**
     * 同步/流式共用：准备 ChatModel + 工具配置
     */
    public void prepare(ChatContext ctx) {
        Long providerId = ctx.getProviderId();
        Map<String, Object> configMap = ctx.getConfigMap();
        Agent agent = ctx.getAgent();

        // 1. 获取 ChatModel
        ChatModel chatModel = modelFactory.getChatModel(providerId);
        ctx.setChatModel(chatModel);

        // 2. 构建工具选项
        ToolCallingChatOptions toolOptions = buildChatOptionsWithTools(providerId, configMap, agent, ctx);
        toolOptions.setInternalToolExecutionEnabled(false);
        ctx.setToolOptions(toolOptions);

        // 3. 构建回调映射
        Map<String, ToolCallback> toolCallbackMap = buildToolCallbackMap(toolOptions);
        ctx.setToolCallbackMap(toolCallbackMap);

        log.info("[Chat] 工具准备完成: providerId={}, 工具数={}, 工具名={}",
                providerId, toolCallbackMap.size(), toolCallbackMap.keySet());
    }

    /**
     * 构建 ChatOptions，包含 Agent 绑定的工具回调
     */
    private ToolCallingChatOptions buildChatOptionsWithTools(Long providerId, Map<String, Object> configMap,
                                                              Agent agent, ChatContext ctx) {
        ToolCallingChatOptions.Builder toolBuilder = ToolCallingChatOptions.builder();
        String modelId = configMap.containsKey("modelId") ? configMap.get("modelId").toString() : null;
        if (modelId != null) toolBuilder.model(modelId);
        if (configMap.containsKey("temperature")) {
            Object v = configMap.get("temperature");
            toolBuilder.temperature(v instanceof Number n ? n.doubleValue() : Double.parseDouble(v.toString()));
        }
        if (configMap.containsKey("topP")) {
            Object v = configMap.get("topP");
            toolBuilder.topP(v instanceof Number n ? n.doubleValue() : Double.parseDouble(v.toString()));
        }
        if (configMap.containsKey("maxTokens")) {
            Object v = configMap.get("maxTokens");
            toolBuilder.maxTokens(v instanceof Number n ? n.intValue() : Integer.parseInt(v.toString()));
        }

        // MiMo 联网搜索使用内置 web_search，不与 Agent 自定义工具混用
        boolean mimoWebSearch = false;
        if (providerId != null) {
            ModelProvider provider = modelProviderService.getById(providerId);
            mimoWebSearch = provider != null && provider.getType() == ModelProviderType.MIMO
                    && Boolean.TRUE.equals(configMap.get(ConfigKeys.Agent.ENABLE_WEB_SEARCH));
        }

        if (agent != null && !mimoWebSearch) {
            List<ToolCallback> allCallbacks = new java.util.ArrayList<>();

            // 1. 加载内置/自定义工具（合并：Agent 自身绑定 + Skill 引入的额外工具）
            java.util.LinkedHashSet<Long> mergedToolIds = new java.util.LinkedHashSet<>(agentService.getToolIds(agent.getId()));
            if (ctx != null && ctx.getSkillExtraToolIds() != null) {
                mergedToolIds.addAll(ctx.getSkillExtraToolIds());
            }
            if (!mergedToolIds.isEmpty()) {
                allCallbacks.addAll(toolService.resolveToolCallbacksByIds(new java.util.ArrayList<>(mergedToolIds)));
            }

            // 1.1 知识库工具自动注入：当 Agent 绑定了知识库时，自动加载 type=knowledge 的工具
            List<Long> knowledgeIds = agentService.getKnowledgeIds(agent.getId());
            if (!knowledgeIds.isEmpty()) {
                List<Tool> knowledgeTools = toolService.list(
                        new LambdaQueryWrapper<Tool>()
                                .eq(Tool::getToolType, ToolType.KNOWLEDGE)
                                .eq(Tool::getStatus, CommonStatus.ACTIVE));
                if (!knowledgeTools.isEmpty()) {
                    List<String> kbToolNames = knowledgeTools.stream().map(Tool::getName).toList();
                    allCallbacks.addAll(toolService.resolveToolCallbacks(kbToolNames));
                    log.info("[Chat] 自动注入知识库工具: agentId={}, knowledgeBases={}, tools={}",
                            agent.getId(), knowledgeIds.size(), kbToolNames);
                }
            }

            // 2. 加载 MCP Server 工具（运行时获取，不落库；同样合并 Agent + Skill 来源）
            java.util.LinkedHashSet<Long> mergedMcpIds = new java.util.LinkedHashSet<>(agentService.getMcpServerIds(agent.getId()));
            if (ctx != null && ctx.getSkillExtraMcpServerIds() != null) {
                mergedMcpIds.addAll(ctx.getSkillExtraMcpServerIds());
            }
            for (Long serverId : mergedMcpIds) {
                try {
                    allCallbacks.addAll(mcpClientService.getToolCallbacks(serverId));
                } catch (Exception e) {
                    log.warn("[Chat] 加载MCP工具失败: serverId={}, error={}", serverId, e.getMessage());
                }
            }

            // 3. SubAgent 委派工具：当 Agent 绑定了至少 1 个 SubAgent 时，注入 delegate_to_subagent 工具
            List<Long> subAgentIds = ctx != null && ctx.getBoundSubAgentIds() != null
                    ? ctx.getBoundSubAgentIds() : agentService.getSubAgentIds(agent.getId());
            if (subAgentIds != null && !subAgentIds.isEmpty()) {
                if (ctx != null) ctx.setBoundSubAgentIds(subAgentIds);
                ToolCallback delegateCb = delegateSubAgentTool.buildCallback(subAgentIds);
                if (delegateCb != null) {
                    allCallbacks.add(delegateCb);
                }
            }

            if (!allCallbacks.isEmpty()) {
                toolBuilder.toolCallbacks(allCallbacks);
                toolBuilder.toolContext(Map.of("agentId", agent.getId()));
                log.info("[Chat] 加载Agent工具: agentId={}, 内置/技能工具={}, MCP Servers={}, SubAgents={}",
                        agent.getId(), mergedToolIds.size(), mergedMcpIds.size(),
                        subAgentIds != null ? subAgentIds.size() : 0);
            }
        }

        return toolBuilder.build();
    }

    /**
     * 从 ToolCallingChatOptions 中提取工具名→ToolCallback 映射
     */
    private Map<String, ToolCallback> buildToolCallbackMap(ToolCallingChatOptions options) {
        List<ToolCallback> callbacks = options.getToolCallbacks();
        if (callbacks == null || callbacks.isEmpty()) {
            return Map.of();
        }
        return callbacks.stream()
                .collect(Collectors.toMap(
                        cb -> cb.getToolDefinition().name(),
                        cb -> cb,
                        (a, b) -> b));
    }
}

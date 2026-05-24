package com.lightbot.service.chat;

import com.lightbot.entity.Agent;
import com.lightbot.model.ModelFactory;
import com.lightbot.service.AgentService;
import com.lightbot.service.McpClientService;
import com.lightbot.service.ToolService;
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
        ToolCallingChatOptions toolOptions = buildChatOptionsWithTools(providerId, configMap, agent);
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
    private ToolCallingChatOptions buildChatOptionsWithTools(Long providerId, Map<String, Object> configMap, Agent agent) {
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

        if (agent != null) {
            List<ToolCallback> allCallbacks = new java.util.ArrayList<>();

            // 1. 加载内置/自定义工具（从 tool 表）
            List<Long> toolIds = agentService.getToolIds(agent.getId());
            if (!toolIds.isEmpty()) {
                allCallbacks.addAll(toolService.resolveToolCallbacksByIds(toolIds));
            }

            // 2. 加载 MCP Server 工具（运行时获取，不落库）
            List<Long> mcpServerIds = agentService.getMcpServerIds(agent.getId());
            for (Long serverId : mcpServerIds) {
                try {
                    allCallbacks.addAll(mcpClientService.getToolCallbacks(serverId));
                } catch (Exception e) {
                    log.warn("[Chat] 加载MCP工具失败: serverId={}, error={}", serverId, e.getMessage());
                }
            }

            if (!allCallbacks.isEmpty()) {
                toolBuilder.toolCallbacks(allCallbacks);
                toolBuilder.toolContext(Map.of("agentId", agent.getId()));
                log.info("[Chat] 加载Agent工具: agentId={}, 内置工具={}, MCP Servers={}",
                        agent.getId(), toolIds.size(), mcpServerIds.size());
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

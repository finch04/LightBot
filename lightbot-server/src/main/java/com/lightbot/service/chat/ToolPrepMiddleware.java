package com.lightbot.service.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lightbot.constant.ConfigKeys;
import com.lightbot.entity.Agent;
import com.lightbot.entity.Skill;
import com.lightbot.entity.Tool;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.ModelProviderType;
import com.lightbot.enums.ToolType;
import com.lightbot.model.ModelFactory;
import com.lightbot.entity.ModelProvider;
import com.lightbot.service.ModelProviderService;
import com.lightbot.service.AgentService;
import com.lightbot.service.McpClientService;
import com.lightbot.service.SkillService;
import com.lightbot.service.ToolService;
import com.lightbot.subagent.DelegateSubAgentTool;
import com.lightbot.util.JsonIdParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
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
    private final SkillService skillService;

    @Autowired
    @Qualifier("lightBotExecutor")
    private Executor lightBotExecutor;

    /** displayName 缓存：toolName → displayName，TTL 5 分钟 */
    private volatile Map<String, String> displayNameCache;
    private volatile long displayNameCacheTime;
    private static final long DISPLAY_NAME_CACHE_TTL_MS = 5 * 60 * 1000L;

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

        // 4. 构建 displayName 映射（前端展示中文名）
        ctx.setToolDisplayNameMap(buildDisplayNameMap(toolCallbackMap));

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
            // 优先使用版本快照中的绑定 ID，避免暂存/发布混淆
            List<Long> baseToolIds = ctx != null && ctx.getVersionToolIds() != null
                    ? ctx.getVersionToolIds() : agentService.getToolIds(agent.getId());
            java.util.LinkedHashSet<Long> mergedToolIds = new java.util.LinkedHashSet<>(baseToolIds);
            if (ctx != null && ctx.getSkillExtraToolIds() != null) {
                mergedToolIds.addAll(ctx.getSkillExtraToolIds());
            }

            // 懒激活：合并已激活 Skill 的依赖 Tool
            Set<String> activatedSlugs = ctx != null ? ctx.getActivatedSkills() : null;
            if (activatedSlugs != null && !activatedSlugs.isEmpty()) {
                Map<String, List<String>> depMap = skillService.buildDependencyMap(activatedSlugs);
                Set<String> allSlugs = expandSkillClosure(activatedSlugs, depMap);
                for (String slug : allSlugs) {
                    Skill skill = skillService.getBySlug(slug);
                    if (skill == null || skill.getStatus() != CommonStatus.ACTIVE) {
                        continue;
                    }
                    mergedToolIds.addAll(JsonIdParser.parseIds(skill.getToolIds()));
                }
                log.info("[Chat] 懒激活 Skill 依赖展开: activated={}, expanded={}", activatedSlugs, allSlugs);
            }

            if (!mergedToolIds.isEmpty()) {
                allCallbacks.addAll(toolService.resolveToolCallbacksByIds(new java.util.ArrayList<>(mergedToolIds)));
            }

            // 1.1 知识库工具自动注入：当 Agent 绑定了知识库时，自动加载 type=knowledge 的工具
            // 排除已被 Agent 手动绑定的工具（mergedToolIds），避免重复注册
            List<Long> knowledgeIds = ctx != null && ctx.getVersionKnowledgeIds() != null
                    ? ctx.getVersionKnowledgeIds() : agentService.getKnowledgeIds(agent.getId());
            if (!knowledgeIds.isEmpty()) {
                List<Tool> knowledgeTools = toolService.list(
                        new LambdaQueryWrapper<Tool>()
                                .eq(Tool::getToolType, ToolType.KNOWLEDGE)
                                .eq(Tool::getStatus, CommonStatus.ACTIVE));
                if (!knowledgeTools.isEmpty()) {
                    List<Tool> autoInjectTools = knowledgeTools.stream()
                            .filter(t -> !mergedToolIds.contains(t.getId()))
                            .toList();
                    if (!autoInjectTools.isEmpty()) {
                        List<String> kbToolNames = autoInjectTools.stream().map(Tool::getName).toList();
                        allCallbacks.addAll(toolService.resolveToolCallbacks(kbToolNames));
                        log.info("[Chat] 自动注入知识库工具: agentId={}, knowledgeBases={}, tools={}",
                                agent.getId(), knowledgeIds.size(), kbToolNames);
                    }
                }
            }

            // 2. 加载 MCP Server 工具（运行时获取，不落库；同样合并 Agent + Skill 来源）
            List<Long> baseMcpIds = ctx != null && ctx.getVersionMcpServerIds() != null
                    ? ctx.getVersionMcpServerIds() : agentService.getMcpServerIds(agent.getId());
            java.util.LinkedHashSet<Long> mergedMcpIds = new java.util.LinkedHashSet<>(baseMcpIds);
            if (ctx != null && ctx.getSkillExtraMcpServerIds() != null) {
                mergedMcpIds.addAll(ctx.getSkillExtraMcpServerIds());
            }

            // 懒激活：合并已激活 Skill 的依赖 MCP
            if (activatedSlugs != null && !activatedSlugs.isEmpty()) {
                Map<String, List<String>> depMap = skillService.buildDependencyMap(activatedSlugs);
                Set<String> allSlugs = expandSkillClosure(activatedSlugs, depMap);
                for (String slug : allSlugs) {
                    Skill skill = skillService.getBySlug(slug);
                    if (skill == null || skill.getStatus() != CommonStatus.ACTIVE) {
                        continue;
                    }
                    mergedMcpIds.addAll(JsonIdParser.parseIds(skill.getMcpServerIds()));
                }
            }

            // 并行加载所有 MCP Server 的工具
            if (!mergedMcpIds.isEmpty()) {
                List<CompletableFuture<List<ToolCallback>>> futures = mergedMcpIds.stream()
                        .map(serverId -> CompletableFuture.supplyAsync(() -> {
                            try {
                                return mcpClientService.getToolCallbacks(serverId);
                            } catch (Exception e) {
                                log.warn("[Chat] 加载MCP工具失败: serverId={}, error={}", serverId, e.getMessage());
                                return List.<ToolCallback>of();
                            }
                        }, lightBotExecutor))
                        .toList();
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                for (CompletableFuture<List<ToolCallback>> f : futures) {
                    allCallbacks.addAll(f.join());
                }
            }

            // 3. SubAgent 委派工具：当 Agent 绑定了至少 1 个 SubAgent 时，注入 delegate_to_subagent 工具
            List<Long> subAgentIds = ctx != null && ctx.getVersionSubAgentIds() != null
                    ? ctx.getVersionSubAgentIds()
                    : (ctx != null && ctx.getBoundSubAgentIds() != null
                            ? ctx.getBoundSubAgentIds() : agentService.getSubAgentIds(agent.getId()));
            if (subAgentIds != null && !subAgentIds.isEmpty()) {
                if (ctx != null) ctx.setBoundSubAgentIds(subAgentIds);
                ToolCallback delegateCb = delegateSubAgentTool.buildCallback(subAgentIds);
                if (delegateCb != null) {
                    allCallbacks.add(delegateCb);
                }
            }

            // 去重：同名工具只保留第一个（如 Agent 手动绑定了 query_knowledge，自动注入不再重复）
            List<ToolCallback> dedupedCallbacks = dedupCallbacks(allCallbacks);

            if (!dedupedCallbacks.isEmpty()) {
                toolBuilder.toolCallbacks(dedupedCallbacks);
                // toolContext 传递 agentId 和 sessionId
                Long sessionId = ctx != null ? ctx.getSessionId() : null;
                toolBuilder.toolContext(Map.of("agentId", agent.getId(), "sessionId", sessionId));
                log.info("[Chat] 加载Agent工具: agentId={}, 内置/技能工具={}, MCP Servers={}, SubAgents={}",
                        agent.getId(), mergedToolIds.size(), mergedMcpIds.size(),
                        subAgentIds != null ? subAgentIds.size() : 0);
            }
        }

        return toolBuilder.build();
    }

    /**
     * 工具回调去重：同名工具只保留第一个
     */
    private List<ToolCallback> dedupCallbacks(List<ToolCallback> callbacks) {
        Set<String> seen = new LinkedHashSet<>();
        List<ToolCallback> result = new ArrayList<>();
        for (ToolCallback cb : callbacks) {
            String name = cb.getToolDefinition().name();
            if (seen.add(name)) {
                result.add(cb);
            }
        }
        if (seen.size() < callbacks.size()) {
            log.warn("[Chat] 工具去重: 原始={}, 去重后={}, 重复工具={}",
                    callbacks.size(), result.size(),
                    callbacks.stream().map(cb -> cb.getToolDefinition().name())
                            .collect(Collectors.groupingBy(n -> n, Collectors.counting()))
                            .entrySet().stream().filter(e -> e.getValue() > 1)
                            .map(Map.Entry::getKey).toList());
        }
        return result;
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

    /**
     * 构建 toolName → displayName 映射（带缓存，TTL 5 分钟）
     * <p>从数据库 Tool 表查询所有已注册工具的 displayName，
     * MCP 工具不在 Tool 表中，fallback 到工具名本身</p>
     */
    private Map<String, String> buildDisplayNameMap(Map<String, ToolCallback> toolCallbackMap) {
        if (toolCallbackMap == null || toolCallbackMap.isEmpty()) {
            return Map.of();
        }
        Map<String, String> allDisplayNames = getDisplayNameCache();
        // 只返回当前请求需要的工具名
        Map<String, String> result = new HashMap<>();
        for (String name : toolCallbackMap.keySet()) {
            String displayName = allDisplayNames.get(name);
            if (displayName != null) {
                result.put(name, displayName);
            }
        }
        return result;
    }

    /**
     * 获取 displayName 缓存（TTL 过期自动刷新）
     */
    private Map<String, String> getDisplayNameCache() {
        Map<String, String> cached = displayNameCache;
        if (cached != null && System.currentTimeMillis() - displayNameCacheTime < DISPLAY_NAME_CACHE_TTL_MS) {
            return cached;
        }
        // 缓存过期，重新查询
        List<Tool> tools = toolService.list(
                new LambdaQueryWrapper<Tool>().eq(Tool::getStatus, CommonStatus.ACTIVE));
        Map<String, String> map = new HashMap<>();
        for (Tool tool : tools) {
            if (tool.getDisplayName() != null && !tool.getDisplayName().isEmpty()) {
                map.put(tool.getName(), tool.getDisplayName());
            }
        }
        displayNameCache = map;
        displayNameCacheTime = System.currentTimeMillis();
        return map;
    }

    /**
     * DFS 展开 Skill 依赖闭包，含循环检测
     *
     * @param selectedSlugs 已激活的 Skill slug 集合
     * @param dependencyMap slug -> skillDependencies 的映射
     * @return 展开后的完整 Skill slug 集合（拓扑序）
     */
    private Set<String> expandSkillClosure(Set<String> selectedSlugs,
                                            Map<String, List<String>> dependencyMap) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> stack = new ArrayDeque<>(selectedSlugs);

        while (!stack.isEmpty()) {
            String slug = stack.pop();
            if (!visited.add(slug)) {
                continue; // 已访问或循环
            }
            List<String> deps = dependencyMap.getOrDefault(slug, List.of());
            for (String dep : deps) {
                if (!visited.contains(dep)) {
                    stack.push(dep);
                }
            }
        }
        return visited;
    }

}

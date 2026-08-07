package com.lightbot.service.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lightbot.constant.ConfigKeys;
import com.lightbot.entity.Agent;
import com.lightbot.entity.McpServer;
import com.lightbot.entity.Skill;
import com.lightbot.entity.Tool;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.ModelProviderType;
import com.lightbot.enums.ToolType;
import com.lightbot.model.ModelFactory;
import com.lightbot.model.DashScopeModelSupport;
import com.lightbot.entity.ModelProvider;
import com.lightbot.service.ModelProviderService;
import com.lightbot.service.AgentService;
import com.lightbot.service.McpClientService;
import com.lightbot.service.McpServerService;
import com.lightbot.service.SessionTodoService;
import com.lightbot.service.SkillService;
import com.lightbot.businesspage.PresentBusinessPageToolEnricher;
import com.lightbot.service.BusinessPageService;
import com.lightbot.tool.builtin.PresentBusinessPageTool;
import com.lightbot.service.LongMemoryPolicyService;
import com.lightbot.service.ToolService;
import com.lightbot.subagent.DelegateSubAgentTool;
import com.lightbot.agent.tool.memory.UserMemoryToolCallbackFactory;
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
    private final McpServerService mcpServerService;
    private final ModelProviderService modelProviderService;
    private final DelegateSubAgentTool delegateSubAgentTool;
    private final SkillService skillService;
    /** 用于把当前会话 todos 快照塞入 ToolContext，供 WriteTodosTool 按 id 合并 */
    private final SessionTodoService sessionTodoService;
    private final UserMemoryToolCallbackFactory userMemoryToolCallbackFactory;
    private final LongMemoryPolicyService longMemoryPolicyService;
    private final BusinessPageService businessPageService;
    private final PresentBusinessPageToolEnricher presentBusinessPageToolEnricher;

    @Autowired
    @Qualifier("lightBotExecutor")
    private Executor lightBotExecutor;

    /** displayName 缓存：toolName → displayName，TTL 5 分钟 */
    private volatile Map<String, String> displayNameCache;
    /** icon 缓存：toolName → icon，与 displayName 同批构建 */
    private volatile Map<String, String> iconCache;
    private volatile long displayNameCacheTime;
    private static final long DISPLAY_NAME_CACHE_TTL_MS = 5 * 60 * 1000L;
    private final Object cacheLock = new Object();

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

        // 1.1 初始化本轮 todos 快照到 ChatContext：
        // WriteTodosTool 按 id 合并的基准必须能跨多次工具调用累积，否则第二次 write_todos 会用空基准覆盖第一次结果。
        // 此处只初始化一次（基于历史），后续每次 write_todos 成功后由 ChatServiceImpl.executeToolCallback 回写
        if (ctx.getCurrentTodosSnapshot() == null) {
            ctx.setCurrentTodosSnapshot(loadCurrentTodos(ctx.getSessionId(), ctx.getRequestId()));
        }

        // 2. 构建工具选项
        ToolCallingChatOptions toolOptions = buildChatOptionsWithTools(providerId, configMap, agent, ctx);
        toolOptions.setInternalToolExecutionEnabled(false);
        ctx.setToolOptions(toolOptions);

        // 3. 构建回调映射
        Map<String, ToolCallback> toolCallbackMap = buildToolCallbackMap(toolOptions);
        ctx.setToolCallbackMap(toolCallbackMap);

        // 4. 构建 displayName 映射（前端展示中文名）
        ctx.setToolDisplayNameMap(buildDisplayNameMap(toolCallbackMap));

        // 5. 构建 icon 映射（前端头像图标）
        ctx.setToolIconMap(buildIconMap(toolCallbackMap, ctx.getMcpToolIconMap()));

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
        ModelProvider provider = providerId != null ? modelProviderService.getById(providerId) : null;
        boolean mimoWebSearch = provider != null && provider.getType() == ModelProviderType.MIMO
                && Boolean.TRUE.equals(configMap.get(ConfigKeys.Agent.ENABLE_WEB_SEARCH));

        if (agent != null && !mimoWebSearch) {
            List<ToolCallback> allCallbacks = new java.util.ArrayList<>();
            List<ToolCallback> mcpCallbacks = new java.util.ArrayList<>();
            Map<String, String> mcpToolIconMap = new HashMap<>();

            // 0. 会话协作工具由父 Agent 自动获得：维护待办并显式交付 outputs/ 文件。
            // SubAgent 运行时不经过该中间件，不能获得这些父会话能力。
            // present_business_page 仅在 Agent 显式绑定业务页后注入（见下方白名单解析）。
            allCallbacks.addAll(toolService.resolveToolCallbacks(
                    List.of("write_todos", "present_artifacts")));

            // 1. 加载内置/自定义工具（合并：Agent 自身绑定 + Skill 引入的额外工具）
            // 优先使用版本快照中的绑定 ID，避免暂存/发布混淆
            List<Long> baseToolIds = ctx != null && ctx.getVersionToolIds() != null
                    ? ctx.getVersionToolIds() : agentService.getToolIds(agent.getId());

            java.util.LinkedHashSet<Long> mergedToolIds = new java.util.LinkedHashSet<>(baseToolIds != null ? baseToolIds : List.of());
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

            // 1.1b 问数工具自动注入：绑定 dataModels（主路径）或旧 datasets 即开启
            List<Long> askDataModelIds = agentService.getDataModelIds(agent.getId());
            List<Long> askDatasetIds = agentService.getDatasetIds(agent.getId());
            boolean askEnabled = (askDataModelIds != null && !askDataModelIds.isEmpty())
                    || (askDatasetIds != null && !askDatasetIds.isEmpty());
            if (askEnabled) {
                List<String> askDataTools = List.of(
                        "ask_data_search_catalog",
                        "ask_data_describe_dataset",
                        "ask_data_execute");
                allCallbacks.addAll(toolService.resolveToolCallbacks(askDataTools));
                log.info("[Chat] 自动注入问数工具: agentId={}, dataModels={}, datasets={}, tools={}",
                        agent.getId(),
                        askDataModelIds != null ? askDataModelIds.size() : 0,
                        askDatasetIds != null ? askDatasetIds.size() : 0,
                        askDataTools);
            }

            // 1.2 用户长期记忆工具自动注入：仅在用户显式开启长期记忆后提供
            boolean memoryToolsInjected = shouldInjectUserMemoryTools(ctx);
            if (memoryToolsInjected) {
                allCallbacks.addAll(userMemoryToolCallbackFactory.buildCallbacks());
                log.info("[Chat] 自动注入用户长期记忆工具: userId={}, agentId={}",
                        ctx.getUserId(), agent.getId());
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
                List<Long> mcpServerIds = new ArrayList<>(mergedMcpIds);
                List<CompletableFuture<List<ToolCallback>>> futures = mcpServerIds.stream()
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
                for (int index = 0; index < futures.size(); index++) {
                    List<ToolCallback> callbacks = futures.get(index).join();
                    allCallbacks.addAll(callbacks);
                    mcpCallbacks.addAll(callbacks);

                    McpServer server = mcpServerService.getById(mcpServerIds.get(index));
                    if (server != null && server.getIcon() != null && !server.getIcon().isBlank()) {
                        callbacks.forEach(callback -> mcpToolIconMap.putIfAbsent(
                                callback.getToolDefinition().name(), server.getIcon()));
                    }
                }
            }

            // 3. SubAgent 委派工具：当 Agent 绑定了至少 1 个 SubAgent 时，注入 delegate_to_subagent 工具
            List<Long> subAgentIds = ctx != null && ctx.getVersionSubAgentIds() != null
                    ? ctx.getVersionSubAgentIds()
                    : (ctx != null && ctx.getBoundSubAgentIds() != null
                            ? ctx.getBoundSubAgentIds() : agentService.getSubAgentIds(agent.getId()));

            if (subAgentIds != null && !subAgentIds.isEmpty()) {
                if (ctx != null) ctx.setBoundSubAgentIds(subAgentIds);
                allCallbacks.addAll(delegateSubAgentTool.buildCallbacks(subAgentIds));
            }

            // 3.1 沙盒写入配套工具补齐：write 的描述提示较长内容可分次续写，依赖 append/read，
            // 若绑定只含 write 而缺 append/read，模型调用 append 会撞「工具不存在」。此处强制补齐。
            ensureSandboxWriteCompanions(allCallbacks);

            // 业务页组件：须在 Agent 侧显式绑定；未绑定则不注入工具。最终可用 = Agent 绑定 ∩ API Key 白名单
            Long apiKeyId = ctx != null && ctx.getRequest() != null ? ctx.getRequest().getApiKeyId() : null;
            List<String> agentAllowedPages = parseAllowedBusinessPages(configMap);
            Set<String> allowedBusinessPages =
                    businessPageService.resolveAllowedPageTypes(apiKeyId, agentAllowedPages);
            if (!allowedBusinessPages.isEmpty()) {
                allCallbacks.addAll(toolService.resolveToolCallbacks(
                        List.of(PresentBusinessPageTool.TOOL_NAME)));
            }

            // 去重：同名工具只保留第一个（如 Agent 手动绑定了 query_knowledge，自动注入不再重复）
            List<ToolCallback> dedupedCallbacks = dedupCallbacks(allCallbacks);
            // 将可用 pageType 写入工具描述 + schema enum，避免模型自造别名
            dedupedCallbacks = presentBusinessPageToolEnricher.enrich(dedupedCallbacks, allowedBusinessPages);
            if (ctx != null) {
                Set<String> activeMcpToolNames = dedupedCallbacks.stream()
                        .filter(mcpCallbacks::contains)
                        .map(callback -> callback.getToolDefinition().name())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                ctx.setMcpToolNames(activeMcpToolNames);
                ctx.setMcpToolIconMap(activeMcpToolNames.stream()
                        .filter(mcpToolIconMap::containsKey)
                        .collect(Collectors.toMap(name -> name, mcpToolIconMap::get)));
            }

            if (!dedupedCallbacks.isEmpty()) {
                toolBuilder.toolCallbacks(dedupedCallbacks);
                // toolContext 传递 agentId、sessionId 和 requestId
                Long sessionId = ctx != null ? ctx.getSessionId() : null;
                String requestId = ctx != null ? ctx.getRequestId() : null;
                Map<String, Object> toolCtxMap = new HashMap<>();
                toolCtxMap.put("agentId", agent.getId());
                toolCtxMap.put("sessionId", sessionId);
                toolCtxMap.put("parentThreadId", sessionId != null ? sessionId.toString() : "default");
                if (requestId != null) {
                    toolCtxMap.put("requestId", requestId);
                }
                if (ctx != null) toolCtxMap.put("userId", ctx.getUserId());
                if (ctx != null && ctx.getRequest() != null) {
                    if (ctx.getRequest().getApiKeyId() != null) {
                        toolCtxMap.put("apiKeyId", ctx.getRequest().getApiKeyId());
                    }
                    if (ctx.getRequest().getExternalUserId() != null) {
                        toolCtxMap.put("externalUserId", ctx.getRequest().getExternalUserId());
                    }
                    if (ctx.getRequest().getCallerContext() != null
                            && !ctx.getRequest().getCallerContext().isEmpty()) {
                        toolCtxMap.put("callerContext", ctx.getRequest().getCallerContext().toMap());
                        if (ctx.getRequest().getCallerContext().getRegionId() != null) {
                            toolCtxMap.put("regionId", ctx.getRequest().getCallerContext().getRegionId());
                        }
                        if (ctx.getRequest().getCallerContext().getEnterpriseId() != null) {
                            toolCtxMap.put("enterpriseId", ctx.getRequest().getCallerContext().getEnterpriseId());
                        }
                    }
                }
                // 当前会话 todos 快照：供 WriteTodosTool 按 id 合并，避免 AI 漏传导致丢项
                toolCtxMap.put("currentTodos", loadCurrentTodos(sessionId, requestId));
                toolCtxMap.put("allowedBusinessPages", allowedBusinessPages);
                // MCP serializes ToolContext as JSON-RPC _meta; ChatContext is injected only for non-MCP execution.
                toolBuilder.toolContext(toolCtxMap);
                log.info("[Chat] 加载Agent工具: agentId={}, 内置/技能工具={}, MCP Servers={}, SubAgents={}, MemoryTools={}",
                        agent.getId(), mergedToolIds.size(), mergedMcpIds.size(),
                        subAgentIds != null ? subAgentIds.size() : 0, memoryToolsInjected);
            }
        }

        ToolCallingChatOptions options = toolBuilder.build();
        if (provider != null && provider.getType() == ModelProviderType.DASHSCOPE
                && !DashScopeModelSupport.isCompatibleMode(provider.getBaseUrl())) {
            options = DashScopeModelSupport.buildNativeChatOptions(
                    modelId, configMap, options.getToolCallbacks(), options.getToolContext());
            if (DashScopeModelSupport.requiresMultimodalApi(modelId)) {
                log.info("[Chat] DashScope multimodal-generation 路由: modelId={}", modelId);
            }
        }
        if (provider != null) {
            options = modelFactory.adaptToolCallingOptions(provider, configMap, options);
        }
        return options;
    }

    private boolean shouldInjectUserMemoryTools(ChatContext ctx) {
        if (ctx == null || ctx.getRequest() == null) {
            return false;
        }
        // 自动化调度：不注入
        if (ctx.getRequest().getActorUserId() != null && ctx.getRequest().getApiKeyId() == null) {
            return false;
        }
        // 策略未启用则不注入
        Long apiKeyId = ctx.getRequest().getApiKeyId();
        try {
            if (!Boolean.TRUE.equals(longMemoryPolicyService.resolveEffective(apiKeyId).getEnabled())) {
                return false;
            }
        } catch (Exception e) {
            log.warn("[Chat] 读取长期记忆策略失败: apiKeyId={}, error={}", apiKeyId, e.getMessage());
            return false;
        }
        // 开放 API：有 externalUserId 才注入；控制台：登录用户使用 debug_user_{userId}
        if (apiKeyId != null) {
            String externalUserId = ctx.getRequest().getExternalUserId();
            return externalUserId != null && !externalUserId.isBlank();
        }
        return ctx.getUserId() != null;
    }

    /**
     * 沙盒写入配套工具补齐：只要工具集含 sandbox_write_file，就确保 sandbox_append_file、
     * sandbox_read_file 同时可用。write 描述会提示较长内容可用 append 续写，
     * 若 append 未绑定则模型撞「工具不存在」。
     *
     * @param callbacks 待补齐的工具回调列表（原地追加缺失项）
     */
    private void ensureSandboxWriteCompanions(List<ToolCallback> callbacks) {
        Set<String> present = callbacks.stream()
                .map(cb -> cb.getToolDefinition().name())
                .collect(Collectors.toSet());
        if (!present.contains("sandbox_write_file")) {
            return;
        }
        List<String> missing = new ArrayList<>();
        for (String companion : List.of("sandbox_append_file", "sandbox_read_file")) {
            if (!present.contains(companion)) {
                missing.add(companion);
            }
        }
        if (!missing.isEmpty()) {
            callbacks.addAll(toolService.resolveToolCallbacks(missing));
            log.info("[Chat] 补齐沙盒写入配套工具: {}", missing);
        }
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
            String displayName = allDisplayNames.getOrDefault(name, getBuiltInToolDisplayName(name));
            if (displayName != null) {
                result.put(name, displayName);
            }
        }
        return result;
    }

    /**
     * 构建 toolName → icon 映射
     * <p>从数据库 Tool 表查询图标（Ant Design 图标组件名），
     * MCP 工具及无图标工具不进入映射，前端回退到内置注册表图标或首字母。</p>
     */
    private Map<String, String> buildIconMap(Map<String, ToolCallback> toolCallbackMap,
                                              Map<String, String> mcpToolIconMap) {
        if (toolCallbackMap == null || toolCallbackMap.isEmpty()) {
            return Map.of();
        }
        // 复用缓存构建逻辑（getDisplayNameCache 内同批刷新 iconCache）
        getDisplayNameCache();
        Map<String, String> allIcons = iconCache != null ? iconCache : Map.of();
        Map<String, String> result = new HashMap<>();
        for (String name : toolCallbackMap.keySet()) {
            String icon = allIcons.get(name);
            if (icon != null && !icon.isEmpty()) {
                result.put(name, icon);
            }
        }
        if (mcpToolIconMap != null) {
            mcpToolIconMap.forEach((toolName, icon) -> {
                if (toolCallbackMap.containsKey(toolName) && icon != null && !icon.isBlank()) {
                    result.put(toolName, icon);
                }
            });
        }
        return result;
    }

    private String getBuiltInToolDisplayName(String name) {
        return switch (name) {
            case UserMemoryToolCallbackFactory.SAVE_TOOL_NAME -> "保存长期记忆";
            case UserMemoryToolCallbackFactory.SEARCH_TOOL_NAME -> "查询长期记忆";
            case UserMemoryToolCallbackFactory.DELETE_TOOL_NAME -> "停用长期记忆";
            default -> null;
        };
    }

    /**
     * 获取 displayName 缓存（TTL 过期自动刷新，synchronized 防止惊群效应）
     */
    private Map<String, String> getDisplayNameCache() {
        Map<String, String> cached = displayNameCache;
        if (cached != null && System.currentTimeMillis() - displayNameCacheTime < DISPLAY_NAME_CACHE_TTL_MS) {
            return cached;
        }
        synchronized (cacheLock) {
            // 双重检查：进入锁后再次判断，避免重复重建
            cached = displayNameCache;
            if (cached != null && System.currentTimeMillis() - displayNameCacheTime < DISPLAY_NAME_CACHE_TTL_MS) {
                return cached;
            }
            List<Tool> tools = toolService.list(
                    new LambdaQueryWrapper<Tool>().eq(Tool::getStatus, CommonStatus.ACTIVE));
            Map<String, String> map = new HashMap<>();
            Map<String, String> icons = new HashMap<>();
            for (Tool tool : tools) {
                if (tool.getDisplayName() != null && !tool.getDisplayName().isEmpty()) {
                    map.put(tool.getName(), tool.getDisplayName());
                }
                if (tool.getIcon() != null && !tool.getIcon().isEmpty()) {
                    icons.put(tool.getName(), tool.getIcon());
                }
            }
            displayNameCache = map;
            iconCache = icons;
            displayNameCacheTime = System.currentTimeMillis();
            return map;
        }
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

    /**
     * 加载当前会话/请求的 todos 快照，序列化为 List&lt;Map&lt;String,String&gt;&gt;（id/content/status），
     * 供 WriteTodosTool 按 id 合并时读取。读取失败返回空列表，不影响主链路。
     */
    private List<Map<String, String>> loadCurrentTodos(Long sessionId, String requestId) {
        if (sessionId == null || requestId == null || requestId.isBlank()) {
            return List.of();
        }
        try {
            List<com.lightbot.vo.TodoItemVO> snapshot = sessionTodoService.listByRequest(sessionId, requestId);
            if (snapshot == null || snapshot.isEmpty()) {
                return List.of();
            }
            List<Map<String, String>> result = new ArrayList<>(snapshot.size());
            for (com.lightbot.vo.TodoItemVO item : snapshot) {
                Map<String, String> map = new HashMap<>();
                map.put("id", item.getId());
                map.put("content", item.getContent());
                map.put("status", item.getStatus());
                result.add(map);
            }
            return result;
        } catch (Exception e) {
            log.debug("[Chat] 加载当前 todos 快照失败, sessionId={}, requestId={}, error={}",
                    sessionId, requestId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 解析 Agent.config.allowedBusinessPages。
     * 缺省/null → 空列表（未绑定，不允许任何业务页）；非空列表为显式绑定。
     */
    private List<String> parseAllowedBusinessPages(Map<String, Object> configMap) {
        if (configMap == null || !configMap.containsKey(ConfigKeys.Agent.ALLOWED_BUSINESS_PAGES)) {
            return List.of();
        }
        Object raw = configMap.get(ConfigKeys.Agent.ALLOWED_BUSINESS_PAGES);
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    out.add(String.valueOf(item).trim());
                }
            }
            return out;
        }
        if (raw instanceof String s && !s.isBlank()) {
            return List.of(s.trim());
        }
        return List.of();
    }

}

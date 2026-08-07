package com.lightbot.service.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.constant.ConfigKeys;
import com.lightbot.constant.EnterpriseActors;
import com.lightbot.dto.CallerContext;
import com.lightbot.dto.LlmTraceSpanDTO;
import com.lightbot.entity.Agent;
import com.lightbot.entity.ApiKey;
import com.lightbot.entity.ChatSession;
import com.lightbot.enums.AgentStatus;
import com.lightbot.enums.ErrorCode;
import com.lightbot.model.ModelFactory;
import com.lightbot.model.ProviderResolver;
import com.lightbot.service.AgentService;
import com.lightbot.service.AgentVersionService;
import com.lightbot.service.ApiKeyService;
import com.lightbot.service.ChatSessionService;
import com.lightbot.util.CallerContextUtil;
import com.lightbot.util.ExternalUserIdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 初始化中间件：会话解析、Agent加载、Config解析、Provider确定
 *
 * @author finch
 * @since 2026-05-23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InitMiddleware implements ChatMiddleware {

    private final ChatSessionService chatSessionService;
    private final AgentService agentService;
    private final AgentVersionService agentVersionService;
    private final ApiKeyService apiKeyService;
    private final ModelFactory modelFactory;
    private final ObjectMapper objectMapper;
    private final ProviderResolver providerResolver;

    @Override
    public Flux<String> execute(ChatContext ctx, ChatMiddlewareChain next) {
        long t0 = System.currentTimeMillis();

        // 0. 记录当前用户ID（后续 Mono 线程可能丢失 Sa-Token ThreadLocal）
        resolveUserId(ctx);

        // 1. 解析会话ID，并在对话中切换智能体时更新会话绑定
        Long apiKeyId = ctx.getRequest().getApiKeyId();
        normalizeRequestCallerContext(ctx, apiKeyId);
        Long sessionId = resolveSessionId(ctx.getRequest().getSessionId(), ctx.getRequest().getAgentId(),
                ctx.getUserId(), apiKeyId, ctx.getRequest().getActorUserId(),
                ctx.getRequest().getCallerContext());
        ctx.setSessionId(sessionId);
        // 续聊未传身份时从会话回填，保证本轮记忆/工具隔离命名空间一致
        syncCallerContextFromSession(ctx, apiKeyId, sessionId);
        // 控制台：仍无 externalUserId 时再回落 debug_user（须在会话回填之后，避免续聊与已绑定模拟角色冲突）
        ensureConsoleCallerFallback(ctx, apiKeyId, sessionId);
        bindSessionAgentIfNeeded(sessionId, ctx.getRequest().getAgentId(), ctx.getRequest().getAgentVersionId(), ctx.getRequest().getConfigVersion());
        long t1 = System.currentTimeMillis();
        CallerContext callerContext = ctx.getRequest().getCallerContext();
        String externalUserId = ctx.getRequest().getExternalUserId();
        log.info("[Chat][Trace] 会话解析: {}ms, sessionId={}, externalUserId={}, regionId={}, enterpriseId={}",
                t1 - t0, sessionId, externalUserId,
                callerContext != null ? callerContext.getRegionId() : null,
                callerContext != null ? callerContext.getEnterpriseId() : null);
        Map<String, Object> sessionSpanMeta = new java.util.HashMap<>();
        sessionSpanMeta.put("sessionId", sessionId);
        if (externalUserId != null) {
            sessionSpanMeta.put("externalUserId", externalUserId);
        }
        if (callerContext != null && !callerContext.isEmpty()) {
            sessionSpanMeta.put("callerContext", callerContext.isolationKeys());
        }
        ctx.getSpans().add(LlmTraceSpanDTO.of("s1", null, "session_resolve", t0, t1 - t0, "OK", sessionSpanMeta));

        // 2. 加载Agent配置，并校验企业 API Key 作用域
        Agent agent = loadAgent(ctx.getRequest().getAgentId(), apiKeyId);
        enforceApiKeyAgentAccess(apiKeyId, agent);
        ctx.setAgent(agent);
        long t2 = System.currentTimeMillis();
        log.info("[Chat][Trace] Agent加载: {}ms, agentId={}", t2 - t1, agent != null ? agent.getId() : null);
        ctx.getSpans().add(LlmTraceSpanDTO.of("s2", "s1", "agent_load", t1, t2 - t1, "OK",
                Map.of("agentId", agent != null ? agent.getId() : null, "agentName", agent != null ? agent.getName() : null)));

        // 3. 解析 config（支持指定版本 / 草稿 / 默认线上），同时提取版本绑定 ID
        Map<String, Object> configMap = resolveRuntimeConfigMap(agent, ctx.getRequest(), ctx);
        configMap = applyRequestConfigOverrides(configMap, ctx.getRequest());
        ctx.setConfigMap(configMap);
        ctx.setProviderId(providerResolver.resolveFromConfig(configMap));

        ctx.setStartTime(t0);
        // 会话已解析后再回传 SESSION_ID（勿在链外提前发，否则恒为 null）
        return Flux.just(ToolEventGenerator.SESSION_ID_PREFIX + sessionId)
                .concatWith(next.proceed(ctx));
    }

    /**
     * 同步路径专用：仅初始化，不走 Flux 链
     */
    public void init(ChatContext ctx) {
        resolveUserId(ctx);

        Long apiKeyId = ctx.getRequest().getApiKeyId();
        normalizeRequestCallerContext(ctx, apiKeyId);
        Long sessionId = resolveSessionId(ctx.getRequest().getSessionId(), ctx.getRequest().getAgentId(),
                ctx.getUserId(), apiKeyId, ctx.getRequest().getActorUserId(),
                ctx.getRequest().getCallerContext());
        ctx.setSessionId(sessionId);
        syncCallerContextFromSession(ctx, apiKeyId, sessionId);
        ensureConsoleCallerFallback(ctx, apiKeyId, sessionId);
        bindSessionAgentIfNeeded(sessionId, ctx.getRequest().getAgentId(), ctx.getRequest().getAgentVersionId(), ctx.getRequest().getConfigVersion());

        Agent agent = loadAgent(ctx.getRequest().getAgentId(), apiKeyId);
        enforceApiKeyAgentAccess(apiKeyId, agent);
        ctx.setAgent(agent);

        Map<String, Object> configMap = resolveRuntimeConfigMap(agent, ctx.getRequest(), ctx);
        configMap = applyRequestConfigOverrides(configMap, ctx.getRequest());
        ctx.setConfigMap(configMap);
        ctx.setProviderId(providerResolver.resolveFromConfig(configMap));
    }

    /**
     * 应用请求级配置覆盖（如本轮强制关闭深度思考）
     */
    private Map<String, Object> applyRequestConfigOverrides(Map<String, Object> configMap,
                                                            com.lightbot.dto.ChatRequestDTO request) {
        if (request == null || request.getEnableReasoning() == null) {
            return configMap != null ? configMap : Map.of();
        }
        Map<String, Object> mutable = configMap == null || configMap.isEmpty()
                ? new HashMap<>()
                : new HashMap<>(configMap);
        mutable.put(ConfigKeys.Agent.ENABLE_REASONING, request.getEnableReasoning());
        return mutable;
    }

    /** 优先企业 API Key 虚拟身份，其次登录态，再次 actorUserId（自动化调度） */
    private void resolveUserId(ChatContext ctx) {
        if (ctx.getRequest() != null && ctx.getRequest().getApiKeyId() != null) {
            ctx.setUserId(com.lightbot.constant.EnterpriseActors.API_KEY);
            return;
        }
        try {
            ctx.setUserId(cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong());
        } catch (Exception ignored) {
        }
        if (ctx.getUserId() == null && ctx.getRequest() != null && ctx.getRequest().getActorUserId() != null) {
            ctx.setUserId(ctx.getRequest().getActorUserId());
        }
    }

    /**
     * 对话运行时配置：支持 configVersion；未指定时已发布版本用 agent_version 快照，否则用 agent 表当前值。
     */
    public Map<String, Object> resolveRuntimeConfigMap(Agent agent) {
        return resolveRuntimeConfigMap(agent, null, null);
    }

    public Map<String, Object> resolveRuntimeConfigMap(Agent agent, com.lightbot.dto.ChatRequestDTO request) {
        return resolveRuntimeConfigMap(agent, request, null);
    }

    /**
     * 解析运行时配置，同时从版本快照中提取绑定 ID 存入 ChatContext（单次加载，避免重复查询）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> resolveRuntimeConfigMap(Agent agent, com.lightbot.dto.ChatRequestDTO request, ChatContext ctx) {
        if (agent == null) {
            return Map.of();
        }
        // 优先按 agentVersionId（主键）加载，其次按 configVersion（版本号）加载
        if (request != null && request.getAgentVersionId() != null) {
            Map<String, Object> payload = agentVersionService.loadVersionPayloadById(request.getAgentVersionId());
            if (payload != null && ctx != null) {
                applyVersionBindingIds(ctx, payload);
            }
            if (payload != null) {
                Object cfg = payload.get("config");
                if (cfg instanceof Map<?, ?> cfgMap) {
                    return new java.util.HashMap<>((Map<String, Object>) cfgMap);
                }
            }
            return parseConfig(agent.getConfig());
        }
        if (request != null && request.getConfigVersion() != null) {
            // 显式指定版本：单次加载 payload，同时提取 config 和绑定 ID（避免双重查询）
            Map<String, Object> payload = agentVersionService.loadVersionPayload(agent.getId(), request.getConfigVersion());
            if (payload != null && ctx != null) {
                applyVersionBindingIds(ctx, payload);
            }
            if (payload != null) {
                Object cfg = payload.get("config");
                if (cfg instanceof Map<?, ?> cfgMap) {
                    return new java.util.HashMap<>((Map<String, Object>) cfgMap);
                }
            }
            return parseConfig(agent.getConfig());
        }
        Map<String, Object> configMap = parseConfig(agent.getConfig());
        Map<String, Object> draftConfig = parseConfig(agent.getConfig());
        if (agent.getVersion() != null && agent.getVersion() > 0
                && (agent.getStatus() == AgentStatus.PUBLISHED || agent.getStatus() == AgentStatus.PUBLISHED_EDITING)) {
            Map<String, Object> published = agentVersionService.loadPublishedRuntimeConfig(agent.getId());
            if (published != null) {
                applyPublishedChatFields(agent, published);
                // 从已发布快照中提取绑定 ID
                if (ctx != null) {
                    applyVersionBindingIds(ctx, published);
                }
                Object cfg = published.get("config");
                if (cfg instanceof Map<?, ?> cfgMap) {
                    Map<String, Object> merged = new java.util.HashMap<>((Map<String, Object>) cfgMap);
                    configMap = merged;
                }
            }
        }
        // 模型配置（敏感词、上下文条数等）以 agent 表当前暂存值为准，避免已发布 Agent 对话仍用旧快照
        overlayModelBehaviorConfig(configMap, draftConfig);
        return configMap;
    }

    /**
     * 将编排页「模型配置」类字段从暂存 config 覆盖到运行时 config
     */
    private void overlayModelBehaviorConfig(Map<String, Object> target, Map<String, Object> draft) {
        if (target == null || draft == null || draft.isEmpty()) {
            return;
        }
        String[] keys = {
                ConfigKeys.Agent.PROVIDER_ID,
                "modelId",
                "temperature",
                "topP",
                "maxTokens",
                "presencePenalty",
                "frequencyPenalty",
                ConfigKeys.Agent.USER_SENSITIVE_FILTER_ENABLED,
                ConfigKeys.Agent.USER_SENSITIVE_WORDS,
                ConfigKeys.Agent.SENSITIVE_FILTER_ENABLED,
                ConfigKeys.Agent.SENSITIVE_FILTER_STRATEGY,
                ConfigKeys.Agent.SENSITIVE_FILTER_REPLACE_TEXT,
                ConfigKeys.Agent.SENSITIVE_WORDS,
                "maxContextMessages",
                ConfigKeys.Agent.ENABLE_SUMMARY,
                ConfigKeys.Agent.SUMMARY_THRESHOLD_KB,
                ConfigKeys.Agent.SUMMARY_PROMPT,
                ConfigKeys.Agent.SUMMARY_KEEP_MESSAGES,
                ConfigKeys.Agent.SUMMARY_TOOL_RESULT_TOKEN_LIMIT,
                ConfigKeys.Agent.MAX_EXECUTION_STEPS,
                ConfigKeys.Agent.MODEL_RETRY_TIMES,
                ConfigKeys.Agent.STREAM_OUTPUT,
                ConfigKeys.Agent.MULTIMODAL_ENABLED,
                ConfigKeys.Agent.ENABLE_IMAGE_INPUT,
                ConfigKeys.Agent.ENABLE_VIDEO_INPUT,
                ConfigKeys.Agent.ENABLE_AUDIO_INPUT,
                ConfigKeys.Agent.ENABLE_FILE_READ,
                ConfigKeys.Agent.ENABLE_WEB_SEARCH,
                ConfigKeys.Agent.WEB_SEARCH_FORCE,
                ConfigKeys.Agent.WEB_SEARCH_MAX_KEYWORD,
                ConfigKeys.Agent.ENABLE_TTS,
                ConfigKeys.Agent.ENABLE_REASONING
        };
        for (String key : keys) {
            if (draft.containsKey(key)) {
                target.put(key, draft.get(key));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applyPublishedChatFields(Agent agent, Map<String, Object> published) {
        if (published.get("systemPrompt") instanceof String sp && !sp.isBlank()) {
            agent.setSystemPrompt(sp);
        }
        if (published.get("welcomeMessage") instanceof String wm) {
            agent.setWelcomeMessage(wm);
        }
        if (published.get("recommendedQuestions") != null) {
            try {
                agent.setRecommendedQuestions(objectMapper.writeValueAsString(published.get("recommendedQuestions")));
            } catch (Exception ignored) {
                // 保持 agent 表原值
            }
        }
    }

    /**
     * 加载Agent配置。
     * agentId非空时加载指定Agent；为空时查询用户的默认Agent（企业 API Key 路径必须显式指定）。
     *
     * @param agentId  Agent ID
     * @param apiKeyId 企业 API Key ID（非空表示对外集成调用）
     * @return Agent，可能为 null
     */
    public Agent loadAgent(Long agentId) {
        return loadAgent(agentId, null);
    }

    /**
     * @param agentId  Agent ID
     * @param apiKeyId 企业 API Key ID
     * @return Agent
     */
    public Agent loadAgent(Long agentId, Long apiKeyId) {
        if (agentId != null) {
            Agent agent = agentService.getById(agentId);
            if (agent == null) {
                log.warn("[Chat] Agent不存在，agentId={}", agentId);
            }
            return agent;
        }
        if (apiKeyId != null) {
            throw new BizException(ErrorCode.API_KEY_AGENT_REQUIRED);
        }
        long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        return agentService.getDefaultAgent(userId);
    }

    /**
     * 企业 API Key：必须命中已发布 Agent，且在 Key 绑定白名单内
     *
     * @param apiKeyId API Key ID
     * @param agent    已加载 Agent
     */
    private void enforceApiKeyAgentAccess(Long apiKeyId, Agent agent) {
        if (apiKeyId == null) {
            return;
        }
        if (agent == null || agent.getId() == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND);
        }
        AgentStatus status = agent.getStatus();
        if (status != AgentStatus.PUBLISHED && status != AgentStatus.PUBLISHED_EDITING) {
            // 「完全访问」只放开接口范围，不放开未发布 Agent；提示当前状态便于排查
            String statusDesc = status != null ? status.getDesc() : "未知";
            throw new BizException(ErrorCode.API_KEY_AGENT_NOT_PUBLISHED, statusDesc);
        }
        ApiKey apiKey = apiKeyService.getById(apiKeyId);
        if (apiKey == null) {
            throw new BizException(ErrorCode.API_KEY_NOT_FOUND);
        }
        if (!apiKeyService.checkAgentScope(apiKey, String.valueOf(agent.getId()))) {
            throw new BizException(ErrorCode.API_KEY_AGENT_FORBIDDEN);
        }
    }

    /**
     * 解析config JSONB字符串为Map
     */
    public Map<String, Object> parseConfig(String config) {
        if (config == null || config.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(config, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[Chat] 解析Agent config失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 从版本快照 payload 中提取绑定 ID 存入 ChatContext（单次调用，无额外 DB 查询）
     */
    private void applyVersionBindingIds(ChatContext ctx, Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return;
        }
        ctx.setVersionToolIds(parseLongList(payload.get("toolIds")));
        ctx.setVersionKnowledgeIds(parseLongList(payload.get("knowledgeIds")));
        ctx.setVersionMcpServerIds(parseLongList(payload.get("mcpServerIds")));
        ctx.setVersionSubAgentIds(parseLongList(payload.get("subAgentIds")));
        ctx.setVersionSkillIds(parseLongList(payload.get("skillIds")));
    }

    /** 将 List<Number/String> 统一转为 List<Long>，空则返回 null */
    private List<Long> parseLongList(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<Long> ids = new java.util.ArrayList<>();
        for (Object item : list) {
            if (item instanceof Number n) {
                ids.add(n.longValue());
            } else if (item != null && !String.valueOf(item).isBlank()) {
                try {
                    ids.add(Long.parseLong(String.valueOf(item).trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return ids.isEmpty() ? null : ids;
    }

    /**
     * 规范化 callerContext（兼容顶层 externalUserId）：
     * <ul>
     *   <li>开放 API：合并并校验业务传入值</li>
     *   <li>控制台：只规范化请求中显式传入的模拟身份；debug_user 兜底见 {@link #ensureConsoleCallerFallback}</li>
     *   <li>自动化调度：清空，不启用记忆命名空间</li>
     * </ul>
     */
    private void normalizeRequestCallerContext(ChatContext ctx, Long apiKeyId) {
        if (ctx.getRequest() == null) {
            return;
        }
        if (apiKeyId != null) {
            CallerContext normalized = CallerContextUtil.mergeAndNormalize(
                    ctx.getRequest().getCallerContext(), ctx.getRequest().getExternalUserId());
            applyCallerContextToRequest(ctx, normalized);
            return;
        }
        // 自动化调度不启用长期记忆 / 业务身份
        if (ctx.getRequest().getActorUserId() != null) {
            applyCallerContextToRequest(ctx, null);
            return;
        }
        // 控制台：不在此处强制 debug_user——续聊常省略 callerContext，须先与会话已绑定身份对齐
        CallerContext normalized = CallerContextUtil.mergeAndNormalize(
                ctx.getRequest().getCallerContext(), ctx.getRequest().getExternalUserId());
        applyCallerContextToRequest(ctx, normalized);
    }

    /**
     * 控制台在会话回填后仍无 externalUserId 时，回落 debug_user_{登录用户ID} 并写入会话。
     * <p>必须在 {@link #syncCallerContextFromSession} 之后调用，否则续聊会把 debug_user 与已绑定模拟角色判冲突。</p>
     */
    private void ensureConsoleCallerFallback(ChatContext ctx, Long apiKeyId, Long sessionId) {
        if (apiKeyId != null || ctx.getRequest() == null || sessionId == null) {
            return;
        }
        if (ctx.getRequest().getActorUserId() != null) {
            return;
        }
        CallerContext current = ctx.getRequest().getCallerContext();
        if (current != null && StringUtils.hasText(current.getExternalUserId())) {
            return;
        }
        CallerContext fallback = current != null ? CallerContextUtil.resolveEffective(null, current) : new CallerContext();
        if (fallback == null) {
            fallback = new CallerContext();
        }
        fallback.setExternalUserId(ExternalUserIdUtil.consoleDebugId(ctx.getUserId()));
        applyCallerContextToRequest(ctx, fallback);
        bindSessionCallerContext(sessionId, fallback);
    }

    /**
     * 以会话绑定结果为准回填请求（续聊省略或首轮绑定合并后，Tool/记忆均读请求上的完整身份）
     */
    private void syncCallerContextFromSession(ChatContext ctx, Long apiKeyId, Long sessionId) {
        if (ctx.getRequest() == null || sessionId == null) {
            return;
        }
        // 自动化不回填
        if (apiKeyId == null && ctx.getRequest().getActorUserId() != null) {
            return;
        }
        ChatSession session = chatSessionService.getById(sessionId);
        if (session == null) {
            return;
        }
        CallerContext bound = parseSessionCallerContext(session);
        if (bound != null) {
            applyCallerContextToRequest(ctx, bound);
        }
    }

    /**
     * 解析会话ID：有则复用并校验归属，无则按来源新建
     *
     * @param actorUserId   自动化调度身份（非空表示内部任务，非控制台续聊）
     * @param callerContext 调用方身份（API Key / 控制台模拟角色）
     */
    private Long resolveSessionId(Long sessionId, Long agentId, Long userId, Long apiKeyId,
                                  Long actorUserId, CallerContext callerContext) {
        if (sessionId != null) {
            if (apiKeyId != null) {
                chatSessionService.ensureOwnedByApiKey(sessionId, apiKeyId);
                bindSessionCallerContext(sessionId, callerContext);
                return sessionId;
            }
            ChatSession existing = chatSessionService.getById(sessionId);
            if (existing == null) {
                throw new BizException(ErrorCode.SESSION_NOT_FOUND);
            }
            // 自动化：仅内部调度（actorUserId）可续跑；控制台建设者只读排障，不可续聊
            if (EnterpriseActors.SESSION_SOURCE_AUTOMATION.equals(existing.getSource())) {
                if (actorUserId != null && actorUserId.equals(existing.getUserId())) {
                    return sessionId;
                }
                throw new BizException(ErrorCode.SESSION_NOT_FOUND);
            }
            // API 集成会话：控制台不可续聊
            if (EnterpriseActors.SESSION_SOURCE_API.equals(existing.getSource())) {
                throw new BizException(ErrorCode.SESSION_NOT_FOUND);
            }
            // 控制台对话：仅本人 platform 调试会话可续聊，并绑定/校验模拟身份
            chatSessionService.ensurePlatformOwnedByUser(sessionId, userId);
            bindSessionCallerContext(sessionId, callerContext);
            return sessionId;
        }
        if (apiKeyId != null) {
            return chatSessionService.createApiSession(apiKeyId, agentId, callerContext).getId();
        }
        ChatSession created = chatSessionService.createSession(userId, agentId);
        if (callerContext != null && !callerContext.isEmpty()) {
            bindSessionCallerContext(created.getId(), callerContext);
        }
        return created.getId();
    }

    /**
     * 续聊时绑定/校验会话上的 callerContext：
     * 已绑定隔离主键与请求冲突 → 拒绝；新键或首次绑定 → 回写
     */
    private void bindSessionCallerContext(Long sessionId, CallerContext requestContext) {
        ChatSession session = chatSessionService.getById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.SESSION_NOT_FOUND);
        }
        CallerContext bound = parseSessionCallerContext(session);
        CallerContextUtil.assertIsolationCompatible(bound, requestContext);
        CallerContext effective = CallerContextUtil.resolveEffective(bound, requestContext);
        // 兼容：仅有旧列 external_user_id、无 JSONB 时，仍按原规则校验 externalUserId
        if (bound == null && session.getExternalUserId() != null && !session.getExternalUserId().isBlank()) {
            String reqEu = requestContext != null ? requestContext.getExternalUserId() : null;
            if (reqEu != null && !Objects.equals(session.getExternalUserId(), reqEu)) {
                throw new BizException(ErrorCode.API_EXTERNAL_USER_MISMATCH);
            }
            if (effective == null && reqEu == null) {
                CallerContext legacy = new CallerContext();
                legacy.setExternalUserId(session.getExternalUserId());
                effective = legacy;
            } else if (effective != null && effective.getExternalUserId() == null) {
                effective.setExternalUserId(session.getExternalUserId());
            }
        }
        if (effective == null || effective.isEmpty()) {
            return;
        }
        boolean needPersist = bound == null
                || !Objects.equals(bound.getExternalUserId(), effective.getExternalUserId())
                || !Objects.equals(bound.getRegionId(), effective.getRegionId())
                || !Objects.equals(bound.getEnterpriseId(), effective.getEnterpriseId())
                || (requestContext != null && requestContext.getProfile() != null
                && !requestContext.getProfile().isEmpty());
        if (needPersist) {
            chatSessionService.applyCallerContext(session, effective);
            chatSessionService.updateById(session);
        }
    }

    private void applyCallerContextToRequest(ChatContext ctx, CallerContext callerContext) {
        ctx.getRequest().setCallerContext(callerContext);
        ctx.getRequest().setExternalUserId(callerContext != null ? callerContext.getExternalUserId() : null);
    }

    @SuppressWarnings("unchecked")
    private CallerContext parseSessionCallerContext(ChatSession session) {
        if (session == null) {
            return null;
        }
        if (session.getCallerContext() != null && !session.getCallerContext().isBlank()) {
            try {
                Map<String, Object> map = objectMapper.readValue(session.getCallerContext(), new TypeReference<>() {});
                CallerContext parsed = CallerContext.fromMap(map);
                if (parsed != null) {
                    return parsed;
                }
            } catch (Exception e) {
                log.warn("[Chat] 解析会话 caller_context 失败: sessionId={}, error={}", session.getId(), e.getMessage());
            }
        }
        if (session.getExternalUserId() != null && !session.getExternalUserId().isBlank()) {
            CallerContext legacy = new CallerContext();
            legacy.setExternalUserId(session.getExternalUserId());
            return legacy;
        }
        return null;
    }

    /**
     * 已有会话中用户切换智能体或版本并继续对话时，将会话 agentId 和 agentVersionId 同步为当前所选
     *
     * @param configVersion 配置版本号：0=草稿，>0=发布版本，用于判断是否主动切到草稿
     */
    private void bindSessionAgentIfNeeded(Long sessionId, Long agentId, Long agentVersionId, Integer configVersion) {
        if (sessionId == null || agentId == null) {
            return;
        }
        ChatSession session = chatSessionService.getById(sessionId);
        if (session == null) {
            return;
        }
        boolean agentChanged = !agentId.equals(session.getAgentId());

        Long targetVersionId = resolveSessionAgentVersionId(agentId, agentVersionId, configVersion);
        if (targetVersionId == null) {
            if (agentChanged) {
                chatSessionService.updateSessionAgent(sessionId, agentId, null);
                log.info("[Chat] 会话智能体已更新（无版本快照）: sessionId={}, agentId={}", sessionId, agentId);
            }
            return;
        }

        boolean needUpdate = agentChanged || !java.util.Objects.equals(targetVersionId, session.getAgentVersionId());
        if (needUpdate) {
            chatSessionService.updateSessionAgent(sessionId, agentId, targetVersionId);
            log.info("[Chat] 会话智能体/版本已更新: sessionId={}, agentId={}, agentVersionId={}",
                    sessionId, agentId, targetVersionId);
        }
    }

    /**
     * 解析应写入会话的 agent_version.id：与 configVersion 保持一致，避免前端 agentVersionId 与版本号不一致。
     */
    private Long resolveSessionAgentVersionId(Long agentId, Long agentVersionId, Integer configVersion) {
        if (configVersion != null && configVersion == 0) {
            return agentVersionService.getDraftVersionId(agentId);
        }
        if (agentVersionId != null) {
            return agentVersionId;
        }
        if (configVersion != null && configVersion > 0) {
            return agentVersionService.getPublishedVersionId(agentId, configVersion);
        }
        return null;
    }

}

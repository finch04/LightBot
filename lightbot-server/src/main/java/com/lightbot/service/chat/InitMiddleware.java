package com.lightbot.service.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.constant.ConfigKeys;
import com.lightbot.dto.LlmTraceSpan;
import com.lightbot.entity.Agent;
import com.lightbot.enums.AgentStatus;
import com.lightbot.model.ModelFactory;
import com.lightbot.service.AgentService;
import com.lightbot.service.AgentVersionService;
import com.lightbot.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

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
    private final ModelFactory modelFactory;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Flux<String> execute(ChatContext ctx, ChatMiddlewareChain next) {
        long t0 = System.currentTimeMillis();

        // 1. 解析会话ID
        Long sessionId = resolveSessionId(ctx.getRequest().getSessionId(), ctx.getRequest().getAgentId());
        ctx.setSessionId(sessionId);
        long t1 = System.currentTimeMillis();
        log.info("[Chat][Trace] 会话解析: {}ms, sessionId={}", t1 - t0, sessionId);
        ctx.getSpans().add(buildSpan("s1", null, "session_resolve", t0, t1 - t0, "OK", Map.of("sessionId", sessionId)));

        // 2. 加载Agent配置
        Agent agent = loadAgent(ctx.getRequest().getAgentId());
        ctx.setAgent(agent);
        long t2 = System.currentTimeMillis();
        log.info("[Chat][Trace] Agent加载: {}ms, agentId={}", t2 - t1, agent != null ? agent.getId() : null);
        ctx.getSpans().add(buildSpan("s2", "s1", "agent_load", t1, t2 - t1, "OK",
                Map.of("agentId", agent != null ? agent.getId() : null, "agentName", agent != null ? agent.getName() : null)));

        // 3. 解析 config（支持指定版本 / 草稿 / 默认线上）
        Map<String, Object> configMap = resolveRuntimeConfigMap(agent, ctx.getRequest());
        ctx.setConfigMap(configMap);
        ctx.setProviderId(getProviderId(configMap));

        ctx.setStartTime(t0);
        return next.proceed(ctx);
    }

    /**
     * 同步路径专用：仅初始化，不走 Flux 链
     */
    public void init(ChatContext ctx) {
        Long sessionId = resolveSessionId(ctx.getRequest().getSessionId(), ctx.getRequest().getAgentId());
        ctx.setSessionId(sessionId);

        Agent agent = loadAgent(ctx.getRequest().getAgentId());
        ctx.setAgent(agent);

        Map<String, Object> configMap = resolveRuntimeConfigMap(agent, ctx.getRequest());
        ctx.setConfigMap(configMap);
        ctx.setProviderId(getProviderId(configMap));
    }

    /**
     * 对话运行时配置：支持 configVersion；未指定时已发布版本用 agent_version 快照，否则用 agent 表当前值。
     */
    public Map<String, Object> resolveRuntimeConfigMap(Agent agent) {
        return resolveRuntimeConfigMap(agent, null);
    }

    public Map<String, Object> resolveRuntimeConfigMap(Agent agent, com.lightbot.dto.ChatRequest request) {
        if (agent == null) {
            return Map.of();
        }
        if (request != null && request.getConfigVersion() != null) {
            return agentVersionService.resolveRuntimeForChat(agent, request.getConfigVersion());
        }
        Map<String, Object> configMap = parseConfig(agent.getConfig());
        Map<String, Object> draftConfig = parseConfig(agent.getConfig());
        if (agent.getVersion() != null && agent.getVersion() > 0
                && (agent.getStatus() == AgentStatus.PUBLISHED || agent.getStatus() == AgentStatus.PUBLISHED_EDITING)) {
            Map<String, Object> published = agentVersionService.loadPublishedRuntimeConfig(agent.getId());
            if (published != null) {
                applyPublishedChatFields(agent, published);
                Object cfg = published.get("config");
                if (cfg instanceof Map<?, ?> cfgMap) {
                    @SuppressWarnings("unchecked")
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
                ConfigKeys.Agent.SENSITIVE_FILTER_ENABLED,
                ConfigKeys.Agent.SENSITIVE_FILTER_STRATEGY,
                ConfigKeys.Agent.SENSITIVE_FILTER_REPLACE_TEXT,
                ConfigKeys.Agent.SENSITIVE_WORDS,
                "maxContextMessages",
                ConfigKeys.Agent.ENABLE_SUMMARY,
                ConfigKeys.Agent.SUMMARY_THRESHOLD_KB
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
                agent.setRecommendedQuestions(OBJECT_MAPPER.writeValueAsString(published.get("recommendedQuestions")));
            } catch (Exception ignored) {
                // 保持 agent 表原值
            }
        }
    }

    /**
     * 加载Agent配置。
     * agentId非空时加载指定Agent；为空时查询用户的默认Agent。
     */
    public Agent loadAgent(Long agentId) {
        if (agentId != null) {
            Agent agent = agentService.getById(agentId);
            if (agent == null) {
                log.warn("[Chat] Agent不存在，agentId={}", agentId);
            }
            return agent;
        }
        long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        return agentService.getDefaultAgent(userId);
    }

    /**
     * 解析config JSONB字符串为Map
     */
    public Map<String, Object> parseConfig(String config) {
        if (config == null || config.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(config, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[Chat] 解析Agent config失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 从config Map中获取providerId
     */
    public Long getProviderId(Map<String, Object> configMap) {
        Object providerId = configMap.get(ConfigKeys.Agent.PROVIDER_ID);
        if (providerId != null) {
            return providerId instanceof Number ? ((Number) providerId).longValue() : Long.parseLong(providerId.toString());
        }
        var providers = modelFactory.getAvailableProviderIds();
        if (providers.isEmpty()) {
            throw new IllegalArgumentException("请先在「模型提供商管理」中配置至少一个模型提供商");
        }
        log.info("[Chat] Agent未配置providerId，使用默认提供商: id={}", providers.get(0));
        return providers.get(0);
    }

    /**
     * 获取默认providerId（兜底方案）
     */
    public Long getDefaultProviderId() {
        var providers = modelFactory.getAvailableProviderIds();
        if (providers.isEmpty()) {
            throw new IllegalStateException("没有可用的模型提供商，请先在模型提供商管理页面配置");
        }
        return providers.get(0);
    }

    /**
     * 解析会话ID：有则复用，无则新建
     */
    private Long resolveSessionId(Long sessionId, Long agentId) {
        if (sessionId != null) {
            return sessionId;
        }
        return chatSessionService.createSession(agentId).getId();
    }

    private LlmTraceSpan buildSpan(String spanId, String parentSpanId, String name,
                                    long startTime, long durationMs, String status,
                                    Map<String, Object> attributes) {
        LlmTraceSpan span = new LlmTraceSpan();
        span.setSpanId(spanId);
        span.setParentSpanId(parentSpanId);
        span.setName(name);
        span.setStartTime(startTime);
        span.setDurationMs(durationMs);
        span.setStatus(status);
        span.setAttributes(attributes != null ? attributes : Map.of());
        return span;
    }
}

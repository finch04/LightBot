package com.lightbot.service.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.model.ProviderResolver;
import com.lightbot.constant.ConfigKeys;
import com.lightbot.dto.LlmTraceSpan;
import com.lightbot.entity.Agent;
import com.lightbot.entity.ChatSession;
import com.lightbot.enums.AgentStatus;
import com.lightbot.model.ModelFactory;
import com.lightbot.service.AgentService;
import com.lightbot.service.AgentVersionService;
import com.lightbot.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
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
    private final ObjectMapper objectMapper;
    private final ProviderResolver providerResolver;

    @Override
    public Flux<String> execute(ChatContext ctx, ChatMiddlewareChain next) {
        long t0 = System.currentTimeMillis();

        // 0. 记录当前用户ID（后续 Mono 线程可能丢失 Sa-Token ThreadLocal）
        try {
            ctx.setUserId(cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong());
        } catch (Exception ignored) {
        }

        // 1. 解析会话ID，并在对话中切换智能体时更新会话绑定
        Long sessionId = resolveSessionId(ctx.getRequest().getSessionId(), ctx.getRequest().getAgentId());
        ctx.setSessionId(sessionId);
        bindSessionAgentIfNeeded(sessionId, ctx.getRequest().getAgentId());
        long t1 = System.currentTimeMillis();
        log.info("[Chat][Trace] 会话解析: {}ms, sessionId={}", t1 - t0, sessionId);
        ctx.getSpans().add(LlmTraceSpan.of("s1", null, "session_resolve", t0, t1 - t0, "OK", Map.of("sessionId", sessionId)));

        // 2. 加载Agent配置
        Agent agent = loadAgent(ctx.getRequest().getAgentId());
        ctx.setAgent(agent);
        long t2 = System.currentTimeMillis();
        log.info("[Chat][Trace] Agent加载: {}ms, agentId={}", t2 - t1, agent != null ? agent.getId() : null);
        ctx.getSpans().add(LlmTraceSpan.of("s2", "s1", "agent_load", t1, t2 - t1, "OK",
                Map.of("agentId", agent != null ? agent.getId() : null, "agentName", agent != null ? agent.getName() : null)));

        // 3. 解析 config（支持指定版本 / 草稿 / 默认线上），同时提取版本绑定 ID
        Map<String, Object> configMap = resolveRuntimeConfigMap(agent, ctx.getRequest(), ctx);
        ctx.setConfigMap(configMap);
        ctx.setProviderId(providerResolver.resolveFromConfig(configMap));

        ctx.setStartTime(t0);
        return next.proceed(ctx);
    }

    /**
     * 同步路径专用：仅初始化，不走 Flux 链
     */
    public void init(ChatContext ctx) {
        // 记录当前用户ID
        try {
            ctx.setUserId(cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong());
        } catch (Exception ignored) {
        }

        Long sessionId = resolveSessionId(ctx.getRequest().getSessionId(), ctx.getRequest().getAgentId());
        ctx.setSessionId(sessionId);
        bindSessionAgentIfNeeded(sessionId, ctx.getRequest().getAgentId());

        Agent agent = loadAgent(ctx.getRequest().getAgentId());
        ctx.setAgent(agent);

        Map<String, Object> configMap = resolveRuntimeConfigMap(agent, ctx.getRequest(), ctx);
        ctx.setConfigMap(configMap);
        ctx.setProviderId(providerResolver.resolveFromConfig(configMap));
    }

    /**
     * 对话运行时配置：支持 configVersion；未指定时已发布版本用 agent_version 快照，否则用 agent 表当前值。
     */
    public Map<String, Object> resolveRuntimeConfigMap(Agent agent) {
        return resolveRuntimeConfigMap(agent, null, null);
    }

    public Map<String, Object> resolveRuntimeConfigMap(Agent agent, com.lightbot.dto.ChatRequest request) {
        return resolveRuntimeConfigMap(agent, request, null);
    }

    /**
     * 解析运行时配置，同时从版本快照中提取绑定 ID 存入 ChatContext（单次加载，避免重复查询）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> resolveRuntimeConfigMap(Agent agent, com.lightbot.dto.ChatRequest request, ChatContext ctx) {
        if (agent == null) {
            return Map.of();
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
     * 解析会话ID：有则复用，无则新建
     */
    private Long resolveSessionId(Long sessionId, Long agentId) {
        if (sessionId != null) {
            return sessionId;
        }
        return chatSessionService.createSession(agentId).getId();
    }

    /**
     * 已有会话中用户切换智能体并继续对话时，将会话 agentId 同步为当前所选智能体
     */
    private void bindSessionAgentIfNeeded(Long sessionId, Long agentId) {
        if (sessionId == null || agentId == null) {
            return;
        }
        ChatSession session = chatSessionService.getById(sessionId);
        if (session == null) {
            return;
        }
        if (!agentId.equals(session.getAgentId())) {
            chatSessionService.updateAgentId(sessionId, agentId);
            log.info("[Chat] 会话智能体已切换: sessionId={}, agentId={}", sessionId, agentId);
        }
    }

}

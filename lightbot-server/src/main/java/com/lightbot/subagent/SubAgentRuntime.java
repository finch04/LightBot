package com.lightbot.subagent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.constant.ChatConstants;
import com.lightbot.constant.ConfigKeys;
import com.lightbot.constant.ToolResultPrefixes;
import com.lightbot.entity.SubAgent;
import com.lightbot.entity.SubAgentRun;
import com.lightbot.mapper.SubAgentRunMapper;
import com.lightbot.model.ModelFactory;
import com.lightbot.model.DashScopeModelSupport;
import com.lightbot.model.ProviderResolver;
import com.lightbot.entity.ModelProvider;
import com.lightbot.enums.ModelProviderType;
import com.lightbot.service.ModelProviderService;
import com.lightbot.service.ToolService;
import com.lightbot.service.chat.ChatContext;
import com.lightbot.service.chat.ToolEventGenerator;
import com.lightbot.util.TextNormalizeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SubAgent 执行器（流式工具循环）
 * <p>对标 Yuxi 的 task 工具内部 invoke：构造独立的 system_prompt + 子任务，
 * 解析 SubAgent.tools（按 name 查表）形成自己的工具集，
 * 走一轮流式工具调用循环，最终返回 assistant 文本给主 Agent。</p>
 *
 * @author finch
 * @since 2026-05-28
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubAgentRuntime {

    private final ModelProviderService modelProviderService;
    private static final int MAX_LOOP_DEPTH = 6;

    private final ModelFactory modelFactory;
    private final ToolService toolService;
    private final ProviderResolver providerResolver;
    private final ObjectMapper objectMapper;
    private final SubAgentRunMapper subAgentRunMapper;
    private final SubAgentThreadManager threadManager;
    private final ToolEventGenerator toolEventGenerator;

    /**
     * 子代理执行结果
     *
     * @param reply     最终回复文本
     * @param threadId  子代理线程 ID
     * @param continued 是否为续跑（true=加载了历史消息）
     */
    public record SubAgentResult(String reply, String threadId, boolean continued) {}

    /**
     * 同步执行一个 SubAgent，返回最终回答文本。
     * <p>模型解析：SubAgent 未配置独立 Provider 时，继承主 Agent 的 providerId + configMap（含版本快照中的 modelId/参数）。</p>
     *
     * @param subAgent         要委派的子智能体
     * @param taskDescription  主 Agent 给的任务描述
     * @param requestId        请求 ID（透传到工具上下文，用于幂等检查）
     * @param threadId         子代理线程 ID（null 表示新建，非 null 表示续跑）
     * @param parentThreadId   父 Agent 线程 ID（用于生成确定性 threadId）
     * @param chatContext      对话上下文（继承主 Agent 模型配置 + 推送流式事件，可为 null）
     */
    public SubAgentResult run(SubAgent subAgent, String taskDescription,
                              String requestId, String threadId, String parentThreadId,
                              ChatContext chatContext) {
        if (subAgent == null) {
            return new SubAgentResult("SubAgent 不存在", null, false);
        }

        // 1. 幂等性检查：同一 requestId 已完成则直接返回
        if (requestId != null && !requestId.isBlank()) {
            SubAgentRun existing = subAgentRunMapper.selectByRequestId(requestId);
            if (existing != null && isTerminal(existing.getStatus())) {
                log.info("[SubAgent] 幂等命中: requestId=[{}], status=[{}]", requestId, existing.getStatus());
                return new SubAgentResult(
                        existing.getReply() != null ? existing.getReply() : "",
                        existing.getThreadId(),
                        true);
            }
        }

        // 2. 确定 threadId
        boolean continued = false;
        if (threadId == null || threadId.isBlank()) {
            threadId = parentThreadId != null
                    ? SubAgentThreadManager.makeChildThreadId(parentThreadId, subAgent.getName(), requestId)
                    : "subagent_" + System.currentTimeMillis();
        }

        long start = System.currentTimeMillis();
        long deadlineMs = start + ChatConstants.TOOL_EXECUTION_TIMEOUT_SECONDS * 1000L;
        log.info("[SubAgent] 委派开始: name={}, threadId={}, taskLen={}",
                subAgent.getName(), threadId, taskDescription != null ? taskDescription.length() : 0);

        // 3. 创建运行记录
        SubAgentRun run = new SubAgentRun();
        run.setThreadId(threadId);
        run.setParentThreadId(parentThreadId != null ? parentThreadId : "");
        run.setSubagentName(subAgent.getName());
        run.setTask(taskDescription);
        run.setStatus("running");
        run.setRequestId(requestId != null ? requestId : threadId);
        run.setStartTime(LocalDateTime.now());
        run.setToolCallCount(0);
        subAgentRunMapper.insert(run);

        try {
            // 4. 解析子 Agent 的工具集合（按 ID 查 tool 表）
            List<String> toolIdStrings = parseToolIds(subAgent.getToolIds());
            List<Long> toolIds = toolIdStrings.stream().map(Long::parseLong).toList();
            List<ToolCallback> toolCallbacks = toolIds.isEmpty()
                    ? List.of()
                    : toolService.resolveToolCallbacksByIds(toolIds);
            Map<String, ToolCallback> toolMap = new HashMap<>();
            for (ToolCallback cb : toolCallbacks) {
                toolMap.put(cb.getToolDefinition().name(), cb);
            }

            // 5. 准备模型：独立配置优先，否则继承主 Agent（含版本快照 configMap）
            ResolvedModel resolved = resolveModel(subAgent, chatContext);
            ChatModel chatModel = modelFactory.getChatModel(resolved.providerId());
            int modelRetryTimes = resolveModelRetryTimes(resolved.configMap());
            log.info("[SubAgent] 模型: name={}, providerId={}, modelId={}, inherit={}",
                    subAgent.getName(), resolved.providerId(),
                    resolved.configMap().get("modelId"),
                    subAgent.getModelId() == null);

            // 6. 构造消息：续跑加载历史，否则新建
            List<Message> messages;
            if (threadManager.threadExists(threadId)) {
                messages = new ArrayList<>(threadManager.loadMessages(threadId));
                if (!messages.isEmpty() && messages.get(0) instanceof SystemMessage) {
                    messages.set(0, new SystemMessage(subAgent.getSystemPrompt() != null ? subAgent.getSystemPrompt() : ""));
                }
                messages.add(new UserMessage(taskDescription != null ? taskDescription : ""));
                continued = true;
            } else {
                messages = new ArrayList<>();
                messages.add(new SystemMessage(subAgent.getSystemPrompt() != null ? subAgent.getSystemPrompt() : ""));
                messages.add(new UserMessage(taskDescription != null ? taskDescription : ""));
            }

            // 7. 构造 ChatOptions（继承主 Agent 的 modelId/temperature 等 + 注入子工具集）
            ToolCallingChatOptions options = buildSubAgentChatOptions(
                    resolved.providerId(), resolved.configMap(), toolCallbacks, subAgent, requestId);

            // 8. 流式工具循环：直至模型返回不含 tool_call 的纯文本，或达到深度上限
            String reply = "";
            int toolCallCount = 0;
            for (int depth = 0; depth < MAX_LOOP_DEPTH; depth++) {
                long remainingMs = deadlineMs - System.currentTimeMillis();
                if (remainingMs <= 0) {
                    String timeoutMsg = "SubAgent 执行超时（" + ChatConstants.TOOL_EXECUTION_TIMEOUT_SECONDS + "秒），请稍后重试";
                    emitSubAgentError(chatContext, subAgent, timeoutMsg, "TIMEOUT");
                    markFailed(run, timeoutMsg, start);
                    return new SubAgentResult(timeoutMsg, threadId, continued);
                }
                StringBuilder replyBuilder = new StringBuilder();
                AssistantMessage assistant;
                try {
                    assistant = streamLlmWithRetry(
                            chatModel, new Prompt(new ArrayList<>(messages), options),
                            subAgent, chatContext, modelRetryTimes, replyBuilder, depth, deadlineMs);
                } catch (Exception e) {
                    String errorMsg = classifyErrorMessage(e);
                    log.error("[SubAgent] 模型调用失败: name={}, depth={}, error={}",
                            subAgent.getName(), depth, e.getMessage(), e);
                    emitSubAgentError(chatContext, subAgent, errorMsg, classifyErrorCode(e));
                    markFailed(run, errorMsg, start);
                    return new SubAgentResult(errorMsg, threadId, false);
                }

                if (assistant == null) {
                    break;
                }
                if (!assistant.hasToolCalls()) {
                    reply = replyBuilder.length() > 0 ? replyBuilder.toString()
                            : (assistant.getText() != null ? assistant.getText() : "");
                    break;
                }

                // 8.2 模型要求调用工具：逐个执行后回填
                messages.add(assistant);
                List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
                for (AssistantMessage.ToolCall tc : assistant.getToolCalls()) {
                    // 推送子工具调用事件
                    pushEvent(chatContext, new ChatContext.SubAgentEvent(
                            "tool_call", subAgent.getName(), tc.name(), 0));

                    String result;
                    ToolCallback cb = toolMap.get(tc.name());
                    if (cb == null) {
                        result = ToolResultPrefixes.failureJson(ToolResultPrefixes.NOT_FOUND + ": " + tc.name());
                    } else {
                        try {
                            result = cb.call(tc.arguments() != null ? tc.arguments() : "{}",
                                    new ToolContext(Map.of(
                                            "subAgentId", subAgent.getId(),
                                            "subAgentName", subAgent.getName(),
                                            "requestId", requestId != null ? requestId : "")));
                        } catch (Exception e) {
                            log.warn("[SubAgent] 工具执行异常: subAgent={}, tool={}, error={}",
                                    subAgent.getName(), tc.name(), e.getMessage());
                            result = ToolResultPrefixes.failureJson(ToolResultPrefixes.FAILURE + ": " + e.getMessage());
                        }
                    }

                    // 推送子工具结果事件
                    pushEvent(chatContext, new ChatContext.SubAgentEvent(
                            "tool_result", subAgent.getName(),
                            truncate(result, 500), 0));

                    toolResponses.add(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), result));
                    toolCallCount++;
                }
                messages.add(ToolResponseMessage.builder().responses(toolResponses).build());
            }

            // 9. 保存消息历史（续跑用）
            threadManager.saveMessages(threadId, messages);

            // 10. 更新运行记录为完成
            String finalReply = reply.isBlank()
                    ? "（SubAgent " + subAgent.getName() + " 未返回有效内容）"
                    : TextNormalizeUtil.sanitizeForAiMessage(reply, 0);
            long cost = System.currentTimeMillis() - start;
            run.setReply(finalReply);
            run.setStatus("completed");
            run.setToolCallCount(toolCallCount);
            run.setEndTime(LocalDateTime.now());
            subAgentRunMapper.updateById(run);
            log.info("[SubAgent] 委派完成: name={}, 耗时={}ms, replyLen={}", subAgent.getName(), cost, reply.length());
            return new SubAgentResult(finalReply, threadId, continued);

        } catch (Exception e) {
            String errorMsg = "SubAgent 执行失败: " + e.getMessage();
            emitSubAgentError(chatContext, subAgent, errorMsg, "UNKNOWN");
            markFailed(run, errorMsg, start);
            return new SubAgentResult(errorMsg, threadId, false);
        }
    }

    /** 解析后的模型配置：Provider ID + 模型参数字典 */
    private record ResolvedModel(Long providerId, Map<String, Object> configMap) {}

    /**
     * 模型解析：SubAgent 独立 Provider 优先；否则继承主 Agent 的 providerId + configMap（含版本快照）
     */
    private ResolvedModel resolveModel(SubAgent subAgent, ChatContext chatContext) {
        if (subAgent.getModelId() != null) {
            Map<String, Object> cfg = new HashMap<>();
            if (subAgent.getLlmModel() != null && !subAgent.getLlmModel().isBlank()) {
                cfg.put("modelId", subAgent.getLlmModel());
            }
            return new ResolvedModel(subAgent.getModelId(), cfg);
        }
        if (chatContext != null && chatContext.getProviderId() != null) {
            Map<String, Object> cfg = chatContext.getConfigMap() != null
                    ? new HashMap<>(chatContext.getConfigMap()) : new HashMap<>();
            return new ResolvedModel(chatContext.getProviderId(), cfg);
        }
        return new ResolvedModel(providerResolver.resolve(), Map.of());
    }

    /**
     * 构建 SubAgent ChatOptions：继承 modelId/temperature 等，并注入子工具集
     */
    private ToolCallingChatOptions buildSubAgentChatOptions(Long providerId,
                                                             Map<String, Object> configMap,
                                                             List<ToolCallback> toolCallbacks,
                                                             SubAgent subAgent, String requestId) {
        String modelId = configMap != null && configMap.get("modelId") != null
                ? configMap.get("modelId").toString() : null;
        Map<String, Object> toolContext = null;
        if (!toolCallbacks.isEmpty()) {
            toolContext = Map.of(
                    "subAgentId", subAgent.getId(),
                    "subAgentName", subAgent.getName(),
                    "requestId", requestId != null ? requestId : "");
        }

        ModelProvider provider = providerId != null ? modelProviderService.getById(providerId) : null;
        if (provider != null && provider.getType() == ModelProviderType.DASHSCOPE
                && !DashScopeModelSupport.isCompatibleMode(provider.getBaseUrl())) {
            return DashScopeModelSupport.buildNativeChatOptions(
                    modelId, configMap, toolCallbacks, toolContext);
        }

        ToolCallingChatOptions.Builder builder = ToolCallingChatOptions.builder();
        if (modelId != null) {
            builder.model(modelId);
        }
        if (configMap != null) {
            if (configMap.containsKey("temperature")) {
                Object v = configMap.get("temperature");
                builder.temperature(v instanceof Number n ? n.doubleValue() : Double.parseDouble(v.toString()));
            }
            if (configMap.containsKey("topP")) {
                Object v = configMap.get("topP");
                builder.topP(v instanceof Number n ? n.doubleValue() : Double.parseDouble(v.toString()));
            }
            if (configMap.containsKey("maxTokens")) {
                Object v = configMap.get("maxTokens");
                builder.maxTokens(v instanceof Number n ? n.intValue() : Integer.parseInt(v.toString()));
            }
        }
        if (!toolCallbacks.isEmpty()) {
            builder.toolCallbacks(toolCallbacks);
            builder.toolContext(toolContext);
        }
        ToolCallingChatOptions options = builder.build();
        options.setInternalToolExecutionEnabled(false);
        if (provider != null) {
            options = modelFactory.adaptToolCallingOptions(provider, configMap, options);
        }
        return options;
    }

    /**
     * 带重试的流式 LLM 调用（对齐主 Agent streamModelWithRetry 策略）
     */
    private AssistantMessage streamLlmWithRetry(ChatModel chatModel, Prompt prompt, SubAgent subAgent,
                                                 ChatContext chatContext, int retryTimes,
                                                 StringBuilder replyBuilder, int depth, long deadlineMs) throws Exception {
        Exception lastError = null;
        for (int attempt = 0; attempt <= retryTimes; attempt++) {
            long remainingMs = deadlineMs - System.currentTimeMillis();
            if (remainingMs <= 0) {
                throw new RuntimeException("SubAgent 执行超时（" + ChatConstants.TOOL_EXECUTION_TIMEOUT_SECONDS + "秒），请稍后重试");
            }
            try {
                return streamLlmOnce(chatModel, prompt, subAgent, chatContext, replyBuilder, remainingMs);
            } catch (Exception e) {
                lastError = e;
                if (attempt < retryTimes) {
                    int retryNo = attempt + 1;
                    long delayMs = Math.min((long) Math.pow(2, attempt) * 1000, Math.max(0, deadlineMs - System.currentTimeMillis()));
                    if (delayMs <= 0) {
                        throw new RuntimeException("SubAgent 执行超时（" + ChatConstants.TOOL_EXECUTION_TIMEOUT_SECONDS + "秒），请稍后重试");
                    }
                    log.warn("[SubAgent] 模型调用失败，第{}次重试，等待{}ms: name={}, depth={}, error={}",
                            retryNo, delayMs, subAgent.getName(), depth, e.getMessage());
                    emitSubAgentErrorRetry(chatContext, subAgent,
                            "SubAgent 连接异常，正在重试 " + retryNo + "/" + retryTimes,
                            classifyErrorCode(e), retryNo, retryTimes);
                    Thread.sleep(delayMs);
                }
            }
        }
        throw lastError != null ? lastError : new RuntimeException("SubAgent 模型调用失败");
    }

    /** 单次流式 LLM 调用（受整体墙钟预算约束，与主 Agent 工具超时一致） */
    private AssistantMessage streamLlmOnce(ChatModel chatModel, Prompt prompt, SubAgent subAgent,
                                          ChatContext chatContext, StringBuilder replyBuilder, long remainingMs) {
        List<AssistantMessage> lastAssistant = new ArrayList<>();
        java.util.concurrent.atomic.AtomicBoolean completed = new java.util.concurrent.atomic.AtomicBoolean(false);
        Flux<ChatResponse> flux = chatModel.stream(prompt);
        flux.doOnNext(response -> {
            Generation gen = response.getResult();
            if (gen != null && gen.getOutput() != null) {
                AssistantMessage output = gen.getOutput();
                if (lastAssistant.isEmpty()) {
                    lastAssistant.add(output);
                } else {
                    lastAssistant.set(0, output);
                }
                String text = output.getText();
                if (text != null && !text.isEmpty()) {
                    replyBuilder.append(text);
                    pushEvent(chatContext, new ChatContext.SubAgentEvent(
                            "token", subAgent.getName(), text, 0));
                }
            }
        }).doOnComplete(() -> completed.set(true))
          .take(Duration.ofMillis(Math.max(1, remainingMs)))
          .blockLast();
        if (!completed.get()) {
            throw new RuntimeException("SubAgent 执行超时（" + ChatConstants.TOOL_EXECUTION_TIMEOUT_SECONDS + "秒），请稍后重试");
        }
        return lastAssistant.isEmpty() ? null : lastAssistant.get(0);
    }

    private int resolveModelRetryTimes(Map<String, Object> configMap) {
        if (configMap == null) return 2;
        Object val = configMap.get(ConfigKeys.Agent.MODEL_RETRY_TIMES);
        if (val instanceof Number n) return Math.max(0, Math.min(10, n.intValue()));
        if (val != null) {
            try { return Math.max(0, Math.min(10, Integer.parseInt(val.toString()))); } catch (Exception ignored) {}
        }
        return 2;
    }

    private void emitSubAgentError(ChatContext chatContext, SubAgent subAgent, String message, String code) {
        if (chatContext == null) return;
        int offset = chatContext.getSubAgentContentOffset() != null ? chatContext.getSubAgentContentOffset() : 0;
        String displayName = subAgent.getDisplayName() != null ? subAgent.getDisplayName() : subAgent.getName();
        String json = toolEventGenerator.subagentErrorEvent(subAgent.getName(), displayName, message, code, offset);
        Map<String, Object> evt = new HashMap<>();
        evt.put("type", "subagent_error");
        evt.put("subagentName", subAgent.getName());
        evt.put("displayName", displayName);
        evt.put("message", message);
        evt.put("code", code);
        evt.put("contentOffset", offset);
        if (chatContext.getToolEventsList() != null) {
            chatContext.getToolEventsList().add(evt);
        }
        chatContext.emitRealtimeStatus(json);
    }

    private void emitSubAgentErrorRetry(ChatContext chatContext, SubAgent subAgent, String message,
                                        String code, int attempt, int maxRetries) {
        if (chatContext == null) return;
        int offset = chatContext.getSubAgentContentOffset() != null ? chatContext.getSubAgentContentOffset() : 0;
        String displayName = subAgent.getDisplayName() != null ? subAgent.getDisplayName() : subAgent.getName();
        String json = toolEventGenerator.subagentErrorRetryEvent(
                subAgent.getName(), displayName, message, code, attempt, maxRetries, offset);
        Map<String, Object> evt = new HashMap<>();
        evt.put("type", "subagent_error_retry");
        evt.put("subagentName", subAgent.getName());
        evt.put("displayName", displayName);
        evt.put("message", message);
        evt.put("code", code);
        evt.put("attempt", attempt);
        evt.put("maxRetries", maxRetries);
        evt.put("contentOffset", offset);
        if (chatContext.getToolEventsList() != null) {
            chatContext.getToolEventsList().add(evt);
        }
        chatContext.emitRealtimeStatus(json);
    }

    private String classifyErrorMessage(Throwable e) {
        if (e == null) return "SubAgent 执行失败：未知错误";
        String msg = e.getMessage();
        if (msg == null) return "SubAgent 执行失败：" + e.getClass().getSimpleName();
        if (msg.contains("timeout") || msg.contains("timed out") || msg.contains("Timeout")) {
            return "SubAgent 响应超时，请稍后重试";
        }
        if (msg.contains("429") || msg.contains("rate") || msg.contains("Rate")) {
            return "SubAgent 请求被限流，请稍后重试";
        }
        if (msg.contains("401") || msg.contains("403")) {
            return "SubAgent 模型认证失败，请检查 API Key 配置";
        }
        return "SubAgent 执行失败：" + (msg.length() > 200 ? msg.substring(0, 200) + "..." : msg);
    }

    private String classifyErrorCode(Throwable e) {
        if (e == null) return "UNKNOWN";
        String msg = e.getMessage();
        if (msg == null) return "UNKNOWN";
        if (msg.contains("timeout") || msg.contains("timed out") || msg.contains("Timeout")) return "TIMEOUT";
        if (msg.contains("429") || msg.contains("rate") || msg.contains("Rate")) return "RATE_LIMITED";
        if (msg.contains("401") || msg.contains("403")) return "AUTH_ERROR";
        if (msg.contains("token") && (msg.contains("limit") || msg.contains("exceed"))) return "TOKEN_LIMIT";
        return "LLM_ERROR";
    }

    private void pushEvent(ChatContext chatContext, ChatContext.SubAgentEvent event) {
        if (chatContext == null || event == null) {
            return;
        }
        int offset = chatContext.getSubAgentContentOffset() != null
                ? chatContext.getSubAgentContentOffset() : event.contentOffset();
        String json = switch (event.type()) {
            case "token" -> toolEventGenerator.subagentTokenEvent(event.subagentName(), event.content(), offset);
            case "tool_call" -> toolEventGenerator.subagentToolCallEvent(event.subagentName(), event.content(), offset);
            case "tool_result" -> toolEventGenerator.subagentToolResultEvent(event.subagentName(), event.content(), offset);
            default -> null;
        };
        if (json == null) {
            chatContext.pushSubAgentEvent(event);
            return;
        }
        Map<String, Object> evt = new HashMap<>();
        switch (event.type()) {
            case "token" -> {
                evt.put("type", "subagent_token");
                evt.put("subagentName", event.subagentName());
                evt.put("content", event.content());
                evt.put("contentOffset", offset);
            }
            case "tool_call" -> {
                evt.put("type", "subagent_tool_call");
                evt.put("subagentName", event.subagentName());
                evt.put("toolName", event.content());
                evt.put("contentOffset", offset);
            }
            case "tool_result" -> {
                evt.put("type", "subagent_tool_result");
                evt.put("subagentName", event.subagentName());
                evt.put("content", event.content());
                evt.put("contentOffset", offset);
            }
            default -> {
                chatContext.pushSubAgentEvent(event);
                return;
            }
        }
        // 流式路径：实时推送并写入 toolEventsList；非流式路径：入队待批量 drain
        if (chatContext.getRealtimeStatusEmitter() != null) {
            if (chatContext.getToolEventsList() != null) {
                chatContext.getToolEventsList().add(evt);
            }
            chatContext.emitRealtimeStatus(json);
        } else {
            chatContext.pushSubAgentEvent(event);
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    private void markFailed(SubAgentRun run, String errorMessage, long start) {
        run.setStatus("failed");
        run.setErrorMessage(errorMessage);
        run.setEndTime(LocalDateTime.now());
        subAgentRunMapper.updateById(run);
        log.error("[SubAgent] 委派失败: name={}, 耗时={}ms, error={}",
                run.getSubagentName(), System.currentTimeMillis() - start, errorMessage);
    }

    private boolean isTerminal(String status) {
        return "completed".equals(status) || "failed".equals(status);
    }

    /** 解析 SubAgent.toolIds JSON 数组 */
    private List<String> parseToolIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[SubAgent] 解析 toolIds JSON 失败: {}", e.getMessage());
            return List.of();
        }
    }
}

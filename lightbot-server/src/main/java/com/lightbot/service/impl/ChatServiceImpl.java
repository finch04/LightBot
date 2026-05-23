package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.constant.ConfigKeys;
import com.lightbot.dto.ChatRequest;
import com.lightbot.dto.RagReferenceVO;
import com.lightbot.entity.Agent;
import com.lightbot.tool.ToolEventEmitter;
import com.lightbot.tool.builtintool.QueryKnowledgeTool;
import com.lightbot.entity.ChatSession;
import com.lightbot.entity.Knowledge;
import com.lightbot.entity.Message;
import com.lightbot.enums.ContentType;
import com.lightbot.enums.MessageRole;
import com.lightbot.mapper.MessageMapper;
import com.lightbot.model.ModelFactory;
import com.lightbot.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * AI对话服务实现类
 *
 * @author finch
 * @since 2026-05-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final MessageMapper messageMapper;
    private final ChatSessionService chatSessionService;
    private final AgentService agentService;
    private final EmbeddingService embeddingService;
    private final KnowledgeService knowledgeService;
    private final EmbeddingModel embeddingModel;
    private final ModelFactory modelFactory;
    private final ToolService toolService;
    private final TaskExecutor taskExecutor;

    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是 LightBot 智能助手。请根据用户的提问，利用可用的工具来提供准确、详细的回答。

            ## 工具使用原则
            - 当工具返回了检索结果或参考资料时，必须基于这些结果来回答用户，不要忽略工具返回的内容
            - 调用工具后，将工具返回的结果作为回答的主要依据
            - 如果工具返回了参考文献，在回答末尾标注来源
            - 如果工具返回"未找到相关内容"，再根据自身知识回答并说明知识库中未找到

            ## 回答规范
            - 使用中文回答
            - 回答应简洁准确
            - 遇到不确定的信息请如实告知
            """;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 状态消息前缀，前端通过此前缀识别状态消息 */
    private static final String STATUS_PREFIX = "[STATUS]";

    /** metadata消息前缀，前端通过此前缀识别metadata消息 */
    private static final String METADATA_PREFIX = "[METADATA]";

    /** 并行检索线程池 */
    private static final ExecutorService RAG_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "rag-search");
        t.setDaemon(true);
        return t;
    });

    @Override
    public String chat(ChatRequest request) {
        // 1. 解析会话ID，无则新建
        Long sessionId = resolveSessionId(request.getSessionId(), request.getAgentId());

        // 2. 加载Agent配置
        Agent agent = loadAgent(request.getAgentId());

        // 3. 解析config获取providerId和配置
        Map<String, Object> configMap = parseConfig(agent != null ? agent.getConfig() : null);
        Long providerId = getProviderId(configMap);

        // 4. 构建消息列表（含系统提示词 + RAG上下文 + 历史消息）
        List<org.springframework.ai.chat.messages.Message> messages = buildMessages(sessionId, request.getMessage(), agent);

        // 5. 通过 ModelFactory 获取 ChatModel，构建 ChatOptions（含工具）
        ChatModel chatModel = modelFactory.getChatModel(providerId);
        ChatOptions options = buildChatOptionsWithTools(providerId, configMap, agent);

        log.info("[Chat] 用户消息: sessionId={}, agentId={}, message={}", sessionId,
                agent != null ? agent.getId() : null, request.getMessage());

        // 6. 调用模型获取回复
        ChatResponse response = chatModel.call(new Prompt(messages, options));
        String reply = response.getResult().getOutput().getText();

        log.info("[Chat] AI回复: sessionId={}, length={}", sessionId, reply != null ? reply.length() : 0);

        // 7. 持久化用户消息和AI回复
        saveMessage(sessionId, MessageRole.USER, request.getMessage());
        saveMessage(sessionId, MessageRole.ASSISTANT, reply);

        // 8. 异步生成标题
        taskExecutor.execute(() -> generateTitle(sessionId));

        return reply;
    }

    @Override
    public Flux<String> chatStream(ChatRequest request) {
        long t0 = System.currentTimeMillis();

        // 1. 解析会话ID
        Long sessionId = resolveSessionId(request.getSessionId(), request.getAgentId());
        long t1 = System.currentTimeMillis();
        log.info("[Chat][Trace] 会话解析: {}ms, sessionId={}", t1 - t0, sessionId);

        // 2. 加载Agent配置
        Agent agent = loadAgent(request.getAgentId());
        long t2 = System.currentTimeMillis();
        log.info("[Chat][Trace] Agent加载: {}ms, agentId={}", t2 - t1, agent != null ? agent.getId() : null);

        // 3. 解析config获取providerId和配置
        Map<String, Object> configMap = parseConfig(agent != null ? agent.getConfig() : null);
        Long providerId = getProviderId(configMap);

        // 4. 保存用户消息
        saveMessage(sessionId, MessageRole.USER, request.getMessage());
        long t3 = System.currentTimeMillis();
        log.info("[Chat][Trace] 消息持久化: {}ms", t3 - t2);

        // 5. 构建消息列表
        List<org.springframework.ai.chat.messages.Message> messages = buildMessages(sessionId, request.getMessage(), agent);
        long t4 = System.currentTimeMillis();
        log.info("[Chat][Trace] 消息构建: {}ms, 历史消息数={}", t4 - t3, messages.size() - 2);

        // 6. 获取 ChatModel + ChatOptions（含工具）
        ChatModel chatModel = modelFactory.getChatModel(providerId);
        ToolCallingChatOptions toolOptions = buildChatOptionsWithTools(providerId, configMap, agent);
        Map<String, ToolCallback> toolCallbackMap = buildToolCallbackMap(toolOptions);
        long t5 = System.currentTimeMillis();
        log.info("[Chat][Trace] 模型+工具加载: {}ms, providerId={}, 工具数={}, 工具名={}",
                t5 - t4, providerId, toolCallbackMap.size(), toolCallbackMap.keySet());

        // 7. 禁用内置工具执行，手动管理工具循环
        toolOptions.setInternalToolExecutionEnabled(false);
        String requestId = String.valueOf(System.nanoTime());
        String[] ragMetadataHolder = {null};
        int[] toolCallCountHolder = {0};
        List<Map<String, Object>> toolEventsList = new ArrayList<>();
        StringBuilder fullReply = new StringBuilder();

        // 8. 单次流式调用 + 递归工具循环（参考 spring-ai-alibaba-admin 的 processToolCallsRecursively 模式）
        long tStreamStart = System.currentTimeMillis();

        // 直接进入递归：首次调用LLM → 检测工具 → 执行 → 递归
        return processToolCallsRecursively(
                        chatModel, messages, toolOptions, toolCallbackMap,
                        agent, fullReply, ragMetadataHolder, toolCallCountHolder, toolEventsList, requestId, 0)
                .doOnComplete(() -> {
                    long t7 = System.currentTimeMillis();
                    log.info("[Chat][Trace] ═══════ 链路汇总 ═══════");
                    log.info("[Chat][Trace] 会话解析:      {}ms", t1 - t0);
                    log.info("[Chat][Trace] Agent加载:      {}ms", t2 - t1);
                    log.info("[Chat][Trace] 消息持久化:    {}ms", t3 - t2);
                    log.info("[Chat][Trace] 消息构建:      {}ms", t4 - t3);
                    log.info("[Chat][Trace] 模型+工具加载:  {}ms", t5 - t4);
                    log.info("[Chat][Trace] 流式输出:      {}ms, 工具调用{}次", t7 - tStreamStart, toolCallCountHolder[0]);
                    log.info("[Chat][Trace] 总耗时:        {}ms, 回复长度={}", t7 - t0, fullReply.length());
                    log.info("[Chat][Trace] ════════════════════════");
                    saveMessage(sessionId, MessageRole.ASSISTANT, fullReply.toString(), ragMetadataHolder[0]);
                    taskExecutor.execute(() -> generateTitle(sessionId));
                }).doOnError(e -> {
                    log.error("[Chat] 流式对话异常: sessionId={}, error={}", sessionId, e.getMessage(), e);
                });
    }

    /**
     * 递归处理工具调用：调用LLM → 检测工具 → 执行 → 重新调用LLM
     * <p>关键设计：LLM调用在方法内部执行，确保工具执行完成后才输出文本，
     * 避免 outer stream 完成导致 inner chain 被取消</p>
     *
     * @param chatModel    聊天模型
     * @param messages     消息历史（会被修改：追加AssistantMessage和ToolResponseMessage）
     * @param toolOptions  工具配置选项
     * @param toolCallbackMap 工具名→回调映射
     * @param agent        当前Agent
     * @param fullReply    累积完整回复文本
     * @param ragMetadataHolder RAG引用元数据（数组包装，允许lambda修改）
     * @param toolCallCountHolder 工具调用计数（数组包装，允许lambda修改）
     * @param toolEventsList 所有工具执行事件列表（收集tool_call/tool_status/tool_result，用于持久化到metadata）
     * @param requestId    请求ID，用于跨线程传递工具搜索结果
     * @param depth        递归深度（防止无限循环）
     * @return Flux<String> 流式输出片段
     */
    private Flux<String> processToolCallsRecursively(
            ChatModel chatModel,
            List<org.springframework.ai.chat.messages.Message> messages,
            ToolCallingChatOptions toolOptions,
            Map<String, ToolCallback> toolCallbackMap,
            Agent agent,
            StringBuilder fullReply,
            String[] ragMetadataHolder,
            int[] toolCallCountHolder,
            List<Map<String, Object>> toolEventsList,
            String requestId,
            int depth) {

        // 防止无限循环
        if (depth >= 10) {
            log.warn("[Chat][Trace] 工具调用递归深度达到上限({})，停止循环", depth);
            return Flux.just("\n[工具调用轮次已达上限，请简化问题后重试]");
        }

        // 1. 调用LLM（流式）
        Prompt prompt = new Prompt(new ArrayList<>(messages), toolOptions);

        return chatModel.stream(prompt)
                .concatMap(response -> {
                    Generation gen = response.getResult();
                    AssistantMessage assistantMsg = (gen != null) ? gen.getOutput() : null;

                    // 2. 无工具调用 → 直接输出文本（结束递归）
                    if (assistantMsg == null || !assistantMsg.hasToolCalls()) {
                        String text = (assistantMsg != null) ? assistantMsg.getText() : "";
                        if (text == null) text = "";

                        // 过滤 thinking/reasoning 内容
                        // 方式1：检查 metadata 中的 reasoningContent（Spring AI 标准方式）
                        // 方式2：检查 text 中是否包含 thinking 标记（兜底）
                        if (gen != null && gen.getOutput() != null) {
                            var metadata = gen.getOutput().getMetadata();
                            if (metadata != null) {
                                Object reasoningContent = metadata.get("reasoningContent");
                                if (reasoningContent != null && !reasoningContent.toString().isBlank()) {
                                    // metadata 中有 reasoningContent，说明当前 chunk 是思考内容，跳过
                                    return Flux.empty();
                                }
                            }
                        }

                        // 兜底：过滤常见的 thinking 标记（某些模型可能不走 metadata）
                        text = stripThinkingContent(text);
                        if (text.isEmpty()) return Flux.empty();

                        fullReply.append(text);
                        return Flux.just(text)
                                .concatWith(Flux.defer(() -> {
                                    if (ragMetadataHolder[0] != null) return Flux.just(METADATA_PREFIX + ragMetadataHolder[0]);
                                    return Flux.empty();
                                }));
                    }

                    // 3. 有工具调用 → 执行工具 → 递归调用LLM
                    messages.add(assistantMsg);
                    List<AssistantMessage.ToolCall> toolCalls = assistantMsg.getToolCalls();
                    log.info("[Chat][Trace] 工具调用(depth={}): {}个工具, names={}", depth, toolCalls.size(),
                            toolCalls.stream().map(AssistantMessage.ToolCall::name).toList());

                    // 逐个构建工具执行链（每个工具：tool_call → 执行 → drain中间事件 → tool_result）
                    List<org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse> toolResponseHolder = new ArrayList<>();
                    List<Map<String, Object>> kbResultsHolder = new ArrayList<>();
                    List<Flux<String>> toolChains = new ArrayList<>();

                    for (AssistantMessage.ToolCall toolCall : toolCalls) {
                        toolCallCountHolder[0]++;
                        String toolName = toolCall.name();
                        String toolArgs = toolCall.arguments();
                        String toolCallId = toolCall.id();

                        log.info("[Chat][Trace] 工具调用详情: name={}, args={}", toolName, toolArgs);

                        // 收集 tool_call 事件到 toolEventsList（用于持久化）
                        toolEventsList.add(Map.of("type", "tool_call", "toolName", toolName, "args", toolArgs != null ? toolArgs : ""));

                        toolChains.add(Flux.concat(
                                // 3.1 发送 tool_call 事件（前端显示调用中）
                                Flux.just(STATUS_PREFIX + generateToolCallEvent(toolName, toolArgs)),
                                // 3.2 执行工具 + drain中间事件 + 发送 tool_result
                                Flux.defer(() -> {
                                    long tToolStart = System.currentTimeMillis();
                                    String toolResult;
                                    ToolCallback callback = toolCallbackMap.get(toolName);
                                    if (callback != null) {
                                        try {
                                            toolResult = callback.call(toolArgs, new ToolContext(Map.of(
                                                    "agentId", agent.getId(),
                                                    "requestId", requestId)));
                                        } catch (Exception e) {
                                            log.error("[Chat] 工具执行异常: name={}, error={}", toolName, e.getMessage(), e);
                                            toolResult = "工具执行失败: " + e.getMessage();
                                        }
                                    } else {
                                        toolResult = "工具不存在: " + toolName;
                                        log.warn("[Chat][Trace] 工具不存在: name={}, 可用工具={}", toolName, toolCallbackMap.keySet());
                                    }
                                    long tToolEnd = System.currentTimeMillis();
                                    log.info("[Chat][Trace] 工具执行结果: name={}, 耗时={}ms, resultLength={}, result={}",
                                            toolName, tToolEnd - tToolStart, toolResult.length(),
                                            toolResult.length() > 500 ? toolResult.substring(0, 500) + "..." : toolResult);

                                    // 收集结果用于构建 ToolResponseMessage
                                    toolResponseHolder.add(new org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse(
                                            toolCallId, toolName, toolResult));

                                    // 知识库搜索结果跨线程读取（工具在 SEARCH_EXECUTOR 执行，通过 requestId 传递）
                                    if ("query_knowledge".equals(toolName)) {
                                        List<Map<String, Object>> kbResults = QueryKnowledgeTool.getSearchResults(requestId);
                                        if (!kbResults.isEmpty()) {
                                            kbResultsHolder.addAll(kbResults);
                                            log.info("[Chat][Trace] 知识库搜索结果已捕获: count={}", kbResults.size());
                                        }
                                    }

                                    // drain ToolEventEmitter 中间状态事件（如知识库检索进度）
                                    List<String> emittedEvents = ToolEventEmitter.drain();
                                    List<String> statusEvents = new ArrayList<>();
                                    for (String event : emittedEvents) {
                                        log.info("[Chat][Trace] 工具中间状态: tool={}, status={}", toolName, event);
                                        toolEventsList.add(Map.of("type", "tool_status", "message", event));
                                        statusEvents.add(STATUS_PREFIX + generateToolStatusEvent(event));
                                    }

                                    // 收集 tool_result 事件到 toolEventsList（用于持久化）
                                    String truncated = toolResult.length() > 2000 ? toolResult.substring(0, 2000) + "..." : toolResult;
                                    toolEventsList.add(Map.of("type", "tool_result", "toolName", toolName, "result", truncated));

                                    // 3.3 发送 tool_result 事件（包含返回值，前端显示用）
                                    return Flux.concat(
                                            Flux.fromIterable(statusEvents),
                                            Flux.just(STATUS_PREFIX + generateToolResultEvent(toolName, toolResult)));
                                })
                        ));
                    }

                    Flux<String> allToolEvents = Flux.concat(toolChains)
                            .concatWith(Flux.just(STATUS_PREFIX + "{\"type\":\"tool_complete\"}"));

                    // 4. 工具全部执行完后：构建 ToolResponseMessage + 合并知识库搜索结果+工具链路到 metadata
                    Flux<String> afterTools = Flux.defer(() -> {
                        messages.add(org.springframework.ai.chat.messages.ToolResponseMessage.builder()
                                .responses(toolResponseHolder).build());

                        // 合并工具链路 + 知识库搜索结果为统一 metadata JSON
                        Map<String, Object> metadataMap = new java.util.LinkedHashMap<>();
                        if (!toolEventsList.isEmpty()) {
                            metadataMap.put("toolEvents", toolEventsList);
                        }
                        if (!kbResultsHolder.isEmpty()) {
                            List<RagReferenceVO> refs = kbResultsHolder.stream().map(row -> {
                                RagReferenceVO vo = new RagReferenceVO();
                                vo.setDocumentName((String) row.get("document_name"));
                                String content = (String) row.get("content");
                                vo.setContentPreview(content != null && content.length() > 200
                                        ? content.substring(0, 200) + "..." : content);
                                Object score = row.get("score");
                                vo.setScore(score != null ? ((Number) score).doubleValue() : null);
                                Object knowledgeId = row.get("knowledge_id");
                                vo.setKnowledgeId(knowledgeId != null ? ((Number) knowledgeId).longValue() : null);
                                Object documentId = row.get("document_id");
                                vo.setDocumentId(documentId != null ? ((Number) documentId).longValue() : null);
                                Object chunkId = row.get("chunk_id");
                                vo.setChunkId(chunkId != null ? ((Number) chunkId).longValue() : null);
                                return vo;
                            }).toList();
                            metadataMap.put("ragReferences", refs);
                        }
                        if (!metadataMap.isEmpty()) {
                            try {
                                ragMetadataHolder[0] = OBJECT_MAPPER.writeValueAsString(metadataMap);
                                log.info("[Chat][Trace] metadata已写入: toolEvents={}, ragReferences={}",
                                        metadataMap.containsKey("toolEvents"),
                                        metadataMap.containsKey("ragReferences"));
                            } catch (Exception e) {
                                log.warn("[Chat] 序列化metadata失败: {}", e.getMessage());
                            }
                        }

                        return Flux.empty();
                    });

                    // 5. 工具执行完后递归调用LLM（流式）— 关键：工具执行阻塞在此处，确保完成后才继续
                    return allToolEvents.concatWith(afterTools)
                            .concatWith(processToolCallsRecursively(
                                    chatModel, messages, toolOptions, toolCallbackMap,
                                    agent, fullReply, ragMetadataHolder, toolCallCountHolder, toolEventsList, requestId, depth + 1));
                });
    }

    /**
     * 构建 ChatOptions，包含 Agent 绑定的工具回调
     * <p>有工具时使用 ToolCallingChatOptions，无工具时退化为普通 ChatOptions</p>
     */
    private ToolCallingChatOptions buildChatOptionsWithTools(Long providerId, Map<String, Object> configMap, Agent agent) {
        // 1. 基础模型配置
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

        // 2. 解析 Agent 绑定的工具
        if (agent != null) {
            List<Long> toolIds = agentService.getToolIds(agent.getId());
            if (!toolIds.isEmpty()) {
                List<ToolCallback> toolCallbacks = toolService.resolveToolCallbacksByIds(toolIds);
                if (!toolCallbacks.isEmpty()) {
                    toolBuilder.toolCallbacks(toolCallbacks);
                    toolBuilder.toolContext(Map.of("agentId", agent.getId()));
                    log.info("[Chat] 加载Agent工具: agentId={}, toolIds={}", agent.getId(), toolIds);
                }
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

    /**
     * 构建工具使用引导文本，追加到系统提示词开头
     * <p>列出可用工具名称和描述，引导AI主动调用工具并基于结果回答</p>
     */
    private String buildToolGuide(List<ToolCallback> toolCallbacks) {
        StringBuilder sb = new StringBuilder("## 可用工具\n");
        sb.append("你有以下工具可以使用，请根据用户问题主动调用合适的工具，并基于工具返回的结果来回答：\n\n");
        for (ToolCallback cb : toolCallbacks) {
            sb.append("- **").append(cb.getToolDefinition().name()).append("**: ")
              .append(cb.getToolDefinition().description()).append("\n");
        }
        sb.append("\n**重要**：调用工具后，必须将工具返回的内容作为回答的主要依据，不要忽略工具返回的结果。");
        return sb.toString();
    }

    /**
     * 生成工具调用状态事件 JSON
     */
    private String generateToolCallEvent(String toolName, String args) {
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of(
                    "type", "tool_call",
                    "toolName", toolName,
                    "args", args));
        } catch (Exception e) {
            return "{\"type\":\"tool_call\",\"toolName\":\"" + toolName + "\"}";
        }
    }

    /**
     * 生成工具结果状态事件 JSON
     */
    private String generateToolResultEvent(String toolName, String result) {
        try {
            // 截断过长的结果
            String truncated = result.length() > 2000 ? result.substring(0, 2000) + "..." : result;
            return OBJECT_MAPPER.writeValueAsString(Map.of(
                    "type", "tool_result",
                    "toolName", toolName,
                    "result", truncated));
        } catch (Exception e) {
            return "{\"type\":\"tool_result\",\"toolName\":\"" + toolName + "\"}";
        }
    }

    /**
     * 生成工具中间状态事件 JSON（如知识库检索进度）
     */
    private String generateToolStatusEvent(String message) {
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of(
                    "type", "tool_status",
                    "message", message));
        } catch (Exception e) {
            return "{\"type\":\"tool_status\",\"message\":\"" + message + "\"}";
        }
    }

    /**
     * 过滤 thinking/reasoning 内容
     * 某些模型（如 Qwen 开启 thinking 模式）会在输出中混入思考过程，
     * 这些内容不应展示给用户
     */
    private String stripThinkingContent(String text) {
        if (text == null || text.isEmpty()) return text;

        // 过滤 <think>...</think> 标签
        text = text.replaceAll("<think>[\\s\\S]*?</think>", "").trim();

        // 过滤 <thinking>...</thinking> 标签
        text = text.replaceAll("<thinking>[\\s\\S]*?</thinking>", "").trim();

        // 过滤常见的 thinking 模式（如 "Let me think", "I need to" 等开头的段落）
        // 这些通常是模型在工具调用前的思考过程
        text = text.replaceAll("(?m)^Let me (think|consider|check|analyze|recap)[.\\.].*$", "").trim();
        text = text.replaceAll("(?m)^I (need to|should|will|must|can)[.\\.].*$", "").trim();
        text = text.replaceAll("(?m)^Pausing.*$", "").trim();
        text = text.replaceAll("(?m)^Let's recap.*$", "").trim();

        return text;
    }

    /**
     * 加载Agent配置。
     * agentId非空时加载指定Agent；为空时查询用户的默认Agent。
     */
    private Agent loadAgent(Long agentId) {
        // 1. 指定了agentId，直接加载
        if (agentId != null) {
            Agent agent = agentService.getById(agentId);
            if (agent == null) {
                log.warn("[Chat] Agent不存在，agentId={}", agentId);
            }
            return agent;
        }

        // 2. 未指定agentId，查询用户的默认Agent
        long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        return agentService.getDefaultAgent(userId);
    }

    /**
     * 解析config JSONB字符串为Map
     */
    private Map<String, Object> parseConfig(String config) {
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
     * 从config Map中获取providerId。
     * 若Agent未配置providerId，自动使用第一个可用的模型提供商。
     */
    private Long getProviderId(Map<String, Object> configMap) {
        Object providerId = configMap.get(ConfigKeys.Agent.PROVIDER_ID);
        if (providerId != null) {
            return providerId instanceof Number ? ((Number) providerId).longValue() : Long.parseLong(providerId.toString());
        }

        // 自动使用第一个可用的模型提供商
        var providers = modelFactory.getAvailableProviderIds();
        if (providers.isEmpty()) {
            throw new IllegalArgumentException("请先在「模型提供商管理」中配置至少一个模型提供商");
        }
        log.info("[Chat] Agent未配置providerId，使用默认提供商: id={}", providers.get(0));
        return providers.get(0);
    }

    /**
     * 构建消息列表：系统提示词 + 工具使用引导 + 最近20条历史 + 当前用户消息
     */
    private List<org.springframework.ai.chat.messages.Message> buildMessages(Long sessionId, String userMessage, Agent agent) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();

        // 1. 系统提示词：优先使用Agent的systemPrompt
        String systemPrompt = (agent != null && agent.getSystemPrompt() != null && !agent.getSystemPrompt().isBlank())
                ? agent.getSystemPrompt()
                : DEFAULT_SYSTEM_PROMPT;

        // 2. 如果Agent绑定了工具，追加工具使用引导到系统提示词
        if (agent != null) {
            List<Long> toolIds = agentService.getToolIds(agent.getId());
            if (!toolIds.isEmpty()) {
                List<ToolCallback> toolCallbacks = toolService.resolveToolCallbacksByIds(toolIds);
                if (!toolCallbacks.isEmpty()) {
                    systemPrompt = buildToolGuide(toolCallbacks) + "\n\n" + systemPrompt;
                }
            }
        }

        messages.add(new SystemMessage(systemPrompt));

        // 2. 加载最近20条历史消息
        List<Message> history = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getSessionId, sessionId)
                        .orderByAsc(Message::getCreateTime)
                        .last("LIMIT 20"));

        for (Message msg : history) {
            if (msg.getRole() == MessageRole.USER) {
                messages.add(new UserMessage(msg.getContent()));
            } else if (msg.getRole() == MessageRole.ASSISTANT) {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }

        // 4. 当前用户消息
        messages.add(new UserMessage(userMessage));
        return messages;
    }

    /**
     * 文本向量化
     */
    private float[] embedText(String text) {
        EmbeddingResponse response = embeddingModel.call(
                new EmbeddingRequest(List.of(text), null));
        return response.getResult().getOutput();
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
     * 持久化消息并更新会话统计
     */
    private void saveMessage(Long sessionId, MessageRole role, String content) {
        saveMessage(sessionId, role, content, null);
    }

    /**
     * 持久化消息并更新会话统计（含metadata）
     */
    private void saveMessage(Long sessionId, MessageRole role, String content, String metadata) {
        Message msg = new Message();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setContentType(ContentType.TEXT);
        msg.setTokenCount(0);
        msg.setMetadata(metadata);
        messageMapper.insert(msg);

        chatSessionService.updateStats(sessionId, 0);
    }

    /**
     * 异步生成对话标题：标题仍为"新对话"且消息数>=2时，调用AI生成简短标题
     */
    private void generateTitle(Long sessionId) {
        try {
            // 1. 检查会话是否存在且标题仍为默认值
            ChatSession session = chatSessionService.getById(sessionId);
            if (session == null || !"新对话".equals(session.getTitle())) {
                return;
            }

            // 2. 获取前4条消息
            List<Message> messages = messageMapper.selectList(
                    new LambdaQueryWrapper<Message>()
                            .eq(Message::getSessionId, sessionId)
                            .orderByAsc(Message::getCreateTime)
                            .last("LIMIT 4"));

            if (messages.size() < 2) {
                return;
            }

            // 3. 拼接对话文本
            StringBuilder conversationText = new StringBuilder();
            for (Message msg : messages) {
                String role = msg.getRole() == MessageRole.USER ? "用户" : "助手";
                conversationText.append(role).append("：").append(msg.getContent()).append("\n");
            }

            // 4. 使用会话绑定的Agent模型生成标题，未绑定则使用默认提供商
            Long providerId = resolveTitleProviderId(session.getAgentId());
            List<org.springframework.ai.chat.messages.Message> promptMessages = new ArrayList<>();
            promptMessages.add(new SystemMessage("你是一个标题生成助手，只输出标题，不要任何其他内容。"));
            promptMessages.add(new UserMessage("请根据以下对话内容生成一个简短的标题（不超过20个字，不要加引号）：\n" + conversationText));

            ChatResponse response = modelFactory.getChatModel(providerId).call(new Prompt(promptMessages));
            String title = response.getResult().getOutput().getText().trim();

            // 5. 清理标题
            title = title.replaceAll("^[\"'「」『』]+|[\"'「」『』]+$", "");
            if (title.length() > 30) {
                title = title.substring(0, 30);
            }

            // 6. 更新会话标题
            if (!title.isBlank()) {
                chatSessionService.updateTitle(sessionId, title);
                log.info("[Chat] 会话标题已生成: sessionId={}, title={}", sessionId, title);
            }
        } catch (Exception e) {
            log.warn("[Chat] 标题生成失败: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    /**
     * 解析标题生成使用的providerId：优先使用会话绑定Agent的模型，未绑定则降级为默认提供商
     */
    private Long resolveTitleProviderId(Long agentId) {
        if (agentId != null) {
            Agent agent = agentService.getById(agentId);
            if (agent != null) {
                Map<String, Object> configMap = parseConfig(agent.getConfig());
                Long providerId = getProviderId(configMap);
                if (providerId != null) {
                    return providerId;
                }
            }
        }
        return getDefaultProviderId();
    }

    /**
     * 获取默认providerId（兜底方案）
     * 优先使用第一个可用的 ModelProvider
     */
    private Long getDefaultProviderId() {
        var providers = modelFactory.getAvailableProviderIds();
        if (providers.isEmpty()) {
            throw new IllegalStateException("没有可用的模型提供商，请先在模型提供商管理页面配置");
        }
        return providers.get(0);
    }

    @Override
    public List<RagReferenceVO> getRagReferences(Long sessionId, Long agentId, String question) {
        // 1. 加载Agent配置
        Agent agent = loadAgent(agentId);
        if (agent == null) {
            return List.of();
        }

        // 2. 获取RAG检索结果
        List<Map<String, Object>> searchResults = getRagSearchResults(agent.getId(), question);

        // 3. 转换为VO
        return searchResults.stream().map(row -> {
            RagReferenceVO vo = new RagReferenceVO();
            vo.setDocumentName((String) row.get("document_name"));
            String content = (String) row.get("content");
            vo.setContentPreview(content != null && content.length() > 200
                    ? content.substring(0, 200) + "..."
                    : content);
            Object score = row.get("score");
            vo.setScore(score != null ? ((Number) score).doubleValue() : null);
            Object knowledgeId = row.get("knowledge_id");
            vo.setKnowledgeId(knowledgeId != null ? ((Number) knowledgeId).longValue() : null);
            Object documentId = row.get("document_id");
            vo.setDocumentId(documentId != null ? ((Number) documentId).longValue() : null);
            Object chunkId = row.get("chunk_id");
            vo.setChunkId(chunkId != null ? ((Number) chunkId).longValue() : null);
            return vo;
        }).toList();
    }

    /**
     * 执行RAG检索，返回原始结果（用于获取引用信息）
     */
    private List<Map<String, Object>> getRagSearchResults(Long agentId, String question) {
        List<Long> knowledgeIds = agentService.getKnowledgeIds(agentId);
        if (knowledgeIds.isEmpty()) {
            return List.of();
        }

        try {
            float[] queryVector = embedText(question);
            List<Map<String, Object>> allResults = new ArrayList<>();

            // 并行检索（使用各知识库配置的 topK 和 threshold）
            List<CompletableFuture<List<Map<String, Object>>>> futures = knowledgeIds.stream()
                    .map(knowledgeId -> CompletableFuture.supplyAsync(() -> {
                        try {
                            Knowledge knowledge = knowledgeService.getById(knowledgeId);
                            int topK = parseRagTopK(knowledge);
                            double threshold = parseRagThreshold(knowledge);
                            return embeddingService.searchSimilar(knowledgeId, queryVector, topK, threshold);
                        } catch (Exception e) {
                            log.warn("[Chat] 知识库检索失败: knowledgeId={}, error={}", knowledgeId, e.getMessage());
                            return List.<Map<String, Object>>of();
                        }
                    }, RAG_EXECUTOR))
                    .toList();

            futures.forEach(f -> allResults.addAll(f.join()));

            return allResults;
        } catch (Exception e) {
            log.warn("[Chat] RAG检索失败: {}", e.getMessage());
            return List.of();
        }
    }

    private static final int DEFAULT_RAG_TOP_K = 5;
    private static final double DEFAULT_RAG_THRESHOLD = 0.5;

    /**
     * 从知识库配置中解析 RAG Top K
     */
    private int parseRagTopK(Knowledge knowledge) {
        if (knowledge == null) {
            return DEFAULT_RAG_TOP_K;
        }
        Map<String, Object> config = parseConfig(knowledge.getConfig());
        Object val = config.get("ragTopK");
        return val instanceof Number ? ((Number) val).intValue() : DEFAULT_RAG_TOP_K;
    }

    /**
     * 从知识库配置中解析 RAG 相似度阈值
     */
    private double parseRagThreshold(Knowledge knowledge) {
        if (knowledge == null) {
            return DEFAULT_RAG_THRESHOLD;
        }
        Map<String, Object> config = parseConfig(knowledge.getConfig());
        Object val = config.get("ragThreshold");
        return val instanceof Number ? ((Number) val).doubleValue() : DEFAULT_RAG_THRESHOLD;
    }
}

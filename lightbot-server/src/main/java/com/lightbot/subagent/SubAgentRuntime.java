package com.lightbot.subagent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.constant.ToolResultPrefixes;
import com.lightbot.entity.SubAgent;
import com.lightbot.entity.SubAgentRun;
import com.lightbot.mapper.SubAgentRunMapper;
import com.lightbot.model.ModelFactory;
import com.lightbot.model.ProviderResolver;
import com.lightbot.service.ToolService;
import com.lightbot.service.chat.ChatContext;
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

    private static final int MAX_LOOP_DEPTH = 6;

    private final ModelFactory modelFactory;
    private final ToolService toolService;
    private final ProviderResolver providerResolver;
    private final ObjectMapper objectMapper;
    private final SubAgentRunMapper subAgentRunMapper;
    private final SubAgentThreadManager threadManager;

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
     *
     * @param subAgent         要委派的子智能体
     * @param taskDescription  主 Agent 给的任务描述
     * @param parentProviderId 主 Agent 当前使用的 providerId（无 modelId 覆盖时复用）
     * @param requestId        请求 ID（透传到工具上下文，用于幂等检查）
     * @param threadId         子代理线程 ID（null 表示新建，非 null 表示续跑）
     * @param parentThreadId   父 Agent 线程 ID（用于生成确定性 threadId）
     * @param chatContext      对话上下文（用于推送流式事件，可为 null）
     */
    public SubAgentResult run(SubAgent subAgent, String taskDescription, Long parentProviderId,
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

            // 5. 准备模型（优先使用 SubAgent 独立配置的 providerId，否则 fallback 到主 Agent）
            Long providerId = subAgent.getModelId() != null
                    ? subAgent.getModelId()
                    : (parentProviderId != null ? parentProviderId : providerResolver.resolve());
            ChatModel chatModel = modelFactory.getChatModel(providerId);

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

            // 7. 构造 ChatOptions，注入工具集合
            ToolCallingChatOptions.Builder toolBuilder = ToolCallingChatOptions.builder();
            if (!toolCallbacks.isEmpty()) {
                toolBuilder.toolCallbacks(toolCallbacks);
                toolBuilder.toolContext(Map.of(
                        "subAgentId", subAgent.getId(),
                        "subAgentName", subAgent.getName(),
                        "requestId", requestId != null ? requestId : ""));
            }
            ToolCallingChatOptions options = toolBuilder.build();
            options.setInternalToolExecutionEnabled(false);

            // 8. 流式工具循环：直至模型返回不含 tool_call 的纯文本，或达到深度上限
            String reply = "";
            int toolCallCount = 0;
            for (int depth = 0; depth < MAX_LOOP_DEPTH; depth++) {
                // 8.1 流式调用 LLM，收集 token 并推送事件
                StringBuilder replyBuilder = new StringBuilder();
                AssistantMessage assistant;
                try {
                    List<AssistantMessage> lastAssistant = new ArrayList<>();
                    Flux<ChatResponse> flux = chatModel.stream(new Prompt(new ArrayList<>(messages), options));
                    flux.doOnNext(response -> {
                        Generation gen = response.getResult();
                        if (gen != null && gen.getOutput() != null) {
                            AssistantMessage output = gen.getOutput();
                            // 收集最新的 AssistantMessage（最后一个包含完整 toolCalls）
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
                    }).blockLast();
                    assistant = lastAssistant.isEmpty() ? null : lastAssistant.get(0);
                } catch (Exception e) {
                    log.error("[SubAgent] 模型调用异常: name={}, depth={}, error={}",
                            subAgent.getName(), depth, e.getMessage(), e);
                    markFailed(run, e.getMessage(), start);
                    return new SubAgentResult("SubAgent 执行失败: " + e.getMessage(), threadId, false);
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
                    ? "（SubAgent " + subAgent.getName() + " 未返回有效内容）" : reply;
            long cost = System.currentTimeMillis() - start;
            run.setReply(finalReply);
            run.setStatus("completed");
            run.setToolCallCount(toolCallCount);
            run.setEndTime(LocalDateTime.now());
            subAgentRunMapper.updateById(run);
            log.info("[SubAgent] 委派完成: name={}, 耗时={}ms, replyLen={}", subAgent.getName(), cost, reply.length());
            return new SubAgentResult(finalReply, threadId, continued);

        } catch (Exception e) {
            markFailed(run, e.getMessage(), start);
            return new SubAgentResult("SubAgent 执行失败: " + e.getMessage(), threadId, false);
        }
    }

    private void pushEvent(ChatContext chatContext, ChatContext.SubAgentEvent event) {
        if (chatContext != null) {
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

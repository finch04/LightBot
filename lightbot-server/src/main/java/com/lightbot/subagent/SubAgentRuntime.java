package com.lightbot.subagent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.SubAgent;
import com.lightbot.model.ModelFactory;
import com.lightbot.service.ToolService;
import com.lightbot.service.chat.InitMiddleware;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SubAgent 执行器（非流式工具循环）
 * <p>对标 Yuxi 的 task 工具内部 invoke：构造独立的 system_prompt + 子任务，
 * 解析 SubAgent.tools（按 name 查表）形成自己的工具集，
 * 走一轮非流式工具调用循环，最终返回 assistant 文本给主 Agent。</p>
 *
 * @author finch
 * @since 2026-05-28
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubAgentRuntime {

    private static final int MAX_LOOP_DEPTH = 6;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ModelFactory modelFactory;
    private final ToolService toolService;
    private final InitMiddleware initMiddleware;

    /**
     * 同步执行一个 SubAgent，返回最终回答文本。
     *
     * @param subAgent       要委派的子智能体
     * @param taskDescription 主 Agent 给的任务描述
     * @param parentProviderId 主 Agent 当前使用的 providerId（无 modelId 覆盖时复用）
     * @param requestId       请求 ID（透传到工具上下文）
     */
    public String run(SubAgent subAgent, String taskDescription, Long parentProviderId, String requestId) {
        if (subAgent == null) {
            return "SubAgent 不存在";
        }
        long start = System.currentTimeMillis();
        log.info("[SubAgent] 委派开始: name={}, taskLen={}",
                subAgent.getName(), taskDescription != null ? taskDescription.length() : 0);

        // 1. 解析子 Agent 的工具集合（按 name 反查 tool 表）
        List<String> toolNames = parseToolNames(subAgent.getTools());
        List<ToolCallback> toolCallbacks = toolNames.isEmpty()
                ? List.of()
                : toolService.resolveToolCallbacks(toolNames);
        Map<String, ToolCallback> toolMap = new HashMap<>();
        for (ToolCallback cb : toolCallbacks) {
            toolMap.put(cb.getToolDefinition().name(), cb);
        }

        // 2. 准备模型（暂不支持 SubAgent 独立的 providerId，按主 Agent 的提供商运行）
        Long providerId = parentProviderId != null ? parentProviderId : initMiddleware.getDefaultProviderId();
        ChatModel chatModel = modelFactory.getChatModel(providerId);

        // 3. 构造消息：系统提示词 + 主 Agent 给的任务
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(subAgent.getSystemPrompt() != null ? subAgent.getSystemPrompt() : ""));
        messages.add(new UserMessage(taskDescription != null ? taskDescription : ""));

        // 4. 构造 ChatOptions，注入工具集合
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

        // 5. 工具循环：直至模型返回不含 tool_call 的纯文本，或达到深度上限
        String reply = "";
        for (int depth = 0; depth < MAX_LOOP_DEPTH; depth++) {
            ChatResponse response;
            try {
                response = chatModel.call(new Prompt(new ArrayList<>(messages), options));
            } catch (Exception e) {
                log.error("[SubAgent] 模型调用异常: name={}, depth={}, error={}",
                        subAgent.getName(), depth, e.getMessage(), e);
                return "SubAgent 执行失败: " + e.getMessage();
            }
            Generation gen = response.getResult();
            AssistantMessage assistant = gen != null ? gen.getOutput() : null;
            if (assistant == null) {
                break;
            }
            if (!assistant.hasToolCalls()) {
                reply = assistant.getText() != null ? assistant.getText() : "";
                break;
            }
            // 模型要求调用工具：逐个执行后回填
            messages.add(assistant);
            List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
            for (AssistantMessage.ToolCall tc : assistant.getToolCalls()) {
                String result;
                ToolCallback cb = toolMap.get(tc.name());
                if (cb == null) {
                    result = "工具不存在: " + tc.name();
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
                        result = "工具执行失败: " + e.getMessage();
                    }
                }
                toolResponses.add(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), result));
            }
            messages.add(ToolResponseMessage.builder().responses(toolResponses).build());
        }
        long cost = System.currentTimeMillis() - start;
        log.info("[SubAgent] 委派完成: name={}, 耗时={}ms, replyLen={}",
                subAgent.getName(), cost, reply.length());
        return reply.isBlank() ? "（SubAgent " + subAgent.getName() + " 未返回有效内容）" : reply;
    }

    /** 解析 SubAgent.tools JSON 数组 */
    private List<String> parseToolNames(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[SubAgent] 解析 tools JSON 失败: {}", e.getMessage());
            return List.of();
        }
    }
}

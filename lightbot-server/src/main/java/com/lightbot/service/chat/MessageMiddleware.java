package com.lightbot.service.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.constant.ConfigKeys;
import com.lightbot.entity.Agent;
import com.lightbot.entity.Message;
import com.lightbot.enums.ContentType;
import com.lightbot.enums.MessageRole;
import com.lightbot.mapper.MessageMapper;
import com.lightbot.model.ModelFactory;
import com.lightbot.service.AgentService;
import com.lightbot.service.ChatSessionService;
import com.lightbot.service.ToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 消息构建中间件：保存用户消息、构建消息列表（含系统提示词+工具引导+历史+摘要）
 *
 * @author finch
 * @since 2026-05-23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageMiddleware implements ChatMiddleware {

    private final MessageMapper messageMapper;
    private final AgentService agentService;
    private final ToolService toolService;
    private final ChatSessionService chatSessionService;
    private final ModelFactory modelFactory;
    private final InitMiddleware initMiddleware;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

            ## 输出格式要求（必须严格遵守，这是最重要的规则）
            你必须使用标准 Markdown 格式输出，严禁输出纯文本段落。

            ### 列表格式（必须使用）
            当回答包含多个要点、步骤、特征时，必须使用列表：
            - 无序列表用 `- ` 开头（注意短横线后有空格）
            - 有序列表用 `1. ` `2. ` `3. ` 开头
            - 每个列表项单独一行，不要合并到同一段落

            ### 标题格式
            - 一级标题：`# 标题`
            - 二级标题：`## 标题`
            - 三级标题：`### 标题`

            ### 表格格式
            当涉及对比、参数、多维信息时使用表格：
            | 列1 | 列2 | 列3 |
            |-----|-----|-----|
            | 值1 | 值2 | 值3 |

            ### 其他格式
            - 重点内容使用 `**加粗**` 标记
            - 代码使用 `` `代码` `` 或代码块
            - 段落之间空一行
            """;

    @Override
    public Flux<String> execute(ChatContext ctx, ChatMiddlewareChain next) {
        // 1. 保存用户消息
        saveMessage(ctx.getSessionId(), MessageRole.USER, ctx.getRequest().getMessage());

        // 2. 构建消息列表
        List<org.springframework.ai.chat.messages.Message> messages = buildMessages(ctx.getSessionId(), ctx.getRequest().getMessage(), ctx.getAgent());
        ctx.setMessages(messages);

        return next.proceed(ctx);
    }

    /**
     * 同步路径专用：保存用户消息 + 构建消息列表
     */
    public void prepare(ChatContext ctx) {
        saveMessage(ctx.getSessionId(), MessageRole.USER, ctx.getRequest().getMessage());
        List<org.springframework.ai.chat.messages.Message> messages = buildMessages(ctx.getSessionId(), ctx.getRequest().getMessage(), ctx.getAgent());
        ctx.setMessages(messages);
    }

    /**
     * 构建消息列表：系统提示词 + 工具使用引导 + 历史消息 + 当前用户消息
     */
    private List<org.springframework.ai.chat.messages.Message> buildMessages(Long sessionId, String userMessage, Agent agent) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();

        // 1. 解析Agent配置，获取上下文条数
        Map<String, Object> agentConfigMap = initMiddleware.parseConfig(agent != null ? agent.getConfig() : null);
        int maxContextMessages = 20;
        if (agentConfigMap.containsKey("maxContextMessages")) {
            Object v = agentConfigMap.get("maxContextMessages");
            maxContextMessages = v instanceof Number ? ((Number) v).intValue() : Integer.parseInt(v.toString());
        }

        // 2. 系统提示词：优先使用Agent的systemPrompt
        String systemPrompt = (agent != null && agent.getSystemPrompt() != null && !agent.getSystemPrompt().isBlank())
                ? agent.getSystemPrompt()
                : DEFAULT_SYSTEM_PROMPT;

        // 3. 如果Agent绑定了工具，追加工具使用引导到系统提示词
        if (agent != null) {
            List<Long> toolIds = agentService.getToolIds(agent.getId());
            if (!toolIds.isEmpty()) {
                List<ToolCallback> toolCallbacks = toolService.resolveToolCallbacksByIds(toolIds);
                if (!toolCallbacks.isEmpty()) {
                    systemPrompt = buildToolGuide(toolCallbacks, agentConfigMap) + "\n\n" + systemPrompt;
                }
            }
        }

        messages.add(new org.springframework.ai.chat.messages.SystemMessage(systemPrompt));

        // 4. 加载历史消息
        List<Message> history = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getSessionId, sessionId)
                        .orderByAsc(Message::getCreateTime)
                        .last("LIMIT " + (maxContextMessages + 1)));

        // 排除最后一条如果就是当前用户消息
        if (!history.isEmpty()) {
            Message lastMsg = history.get(history.size() - 1);
            if (lastMsg.getRole() == MessageRole.USER && userMessage.equals(lastMsg.getContent())) {
                history.remove(history.size() - 1);
            }
        }
        if (history.size() > maxContextMessages) {
            history = history.subList(history.size() - maxContextMessages, history.size());
        }

        // 5. 上下文摘要
        history = summarizeIfNeeded(history, agentConfigMap, agent);

        for (Message msg : history) {
            if (msg.getRole() == MessageRole.USER) {
                messages.add(new UserMessage(msg.getContent()));
            } else if (msg.getRole() == MessageRole.ASSISTANT) {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }

        // 6. 当前用户消息
        messages.add(new UserMessage(userMessage));
        return messages;
    }

    /**
     * 构建工具使用引导文本
     */
    private String buildToolGuide(List<ToolCallback> toolCallbacks, Map<String, Object> agentConfigMap) {
        StringBuilder sb = new StringBuilder("## 可用工具\n");
        sb.append("你有以下工具可以使用，请根据用户问题主动调用合适的工具，并基于工具返回的结果来回答：\n\n");
        for (ToolCallback cb : toolCallbacks) {
            sb.append("- **").append(cb.getToolDefinition().name()).append("**: ")
              .append(cb.getToolDefinition().description()).append("\n");
        }

        boolean asyncEnabled = agentConfigMap != null && Boolean.TRUE.equals(agentConfigMap.get("asyncToolCalls"));
        sb.append("""

                **工具使用规则（必须严格遵守）**：
                """);
        if (asyncEnabled) {
            sb.append("1. 允许同时调用多个工具（并行调用），以提高回答效率\n");
        } else {
            sb.append("1. 每次回复只能调用一个工具，禁止并行调用多个工具\n");
        }
        sb.append("""
                2. 必须等待工具执行完成后，才能基于工具返回的结果生成最终回答
                3. 工具返回的结果必须作为你回答的主要依据，不得忽略或跳过工具返回的内容
                4. 如果工具返回了参考文献或搜索结果，必须在回答中引用这些内容
                5. 如果工具返回"未找到相关内容"，请基于自身知识回答并说明知识库中未找到相关信息
                6. 禁止在工具尚未返回结果时就提前结束对话
                """);

        sb.append("""

                **输出格式要求（必须严格遵守）**：
                - 使用 Markdown 格式输出，合理使用标题、列表、表格等结构化排版
                - 当回答包含多个要点时，必须使用有序列表（1. 2. 3.）或无序列表（- ）
                - 当回答涉及对比、参数、多维信息时，使用 Markdown 表格
                - 每个要点单独一行，不要将多个要点合并到同一段落
                - 重点内容使用 **加粗** 标记
                - 确保标题层级清晰：一级标题用 #，二级用 ##，三级用 ###
                """);
        return sb.toString();
    }

    /**
     * 上下文摘要
     */
    private List<Message> summarizeIfNeeded(List<Message> history, Map<String, Object> configMap, Agent agent) {
        if (!Boolean.TRUE.equals(configMap.get(ConfigKeys.Agent.ENABLE_SUMMARY))) {
            return history;
        }

        long totalChars = history.stream().mapToLong(m -> m.getContent() != null ? m.getContent().length() : 0).sum();
        double totalKb = totalChars / 1024.0;

        double thresholdKb = 100.0;
        Object thresholdVal = configMap.get(ConfigKeys.Agent.SUMMARY_THRESHOLD_KB);
        if (thresholdVal instanceof Number n) {
            thresholdKb = n.doubleValue();
        }

        if (totalKb <= thresholdKb) {
            return history;
        }

        int keepRecent = 6;
        if (history.size() <= keepRecent + 2) {
            return history;
        }

        List<Message> olderMessages = history.subList(0, history.size() - keepRecent);
        List<Message> recentMessages = history.subList(history.size() - keepRecent, history.size());

        try {
            Long providerId = initMiddleware.getProviderId(configMap);
            ChatModel chatModel = modelFactory.getChatModel(providerId);

            StringBuilder conversationText = new StringBuilder();
            for (Message msg : olderMessages) {
                String role = msg.getRole() == MessageRole.USER ? "用户" : "助手";
                conversationText.append(role).append("：").append(msg.getContent()).append("\n");
            }

            List<org.springframework.ai.chat.messages.Message> summaryPrompt = new ArrayList<>();
            summaryPrompt.add(new SystemMessage("你是一个对话摘要助手。请将以下对话内容压缩为简明摘要，保留关键信息、决策和上下文要点。只输出摘要，不要添加额外说明。"));
            summaryPrompt.add(new UserMessage("请对以下对话进行摘要：\n\n" + conversationText));

            ChatResponse response = chatModel.call(new Prompt(summaryPrompt));
            String summary = response.getResult().getOutput().getText().trim();

            if (summary.isBlank()) {
                return history;
            }

            List<Message> result = new ArrayList<>();
            Message summaryMsg = new Message();
            summaryMsg.setRole(MessageRole.SYSTEM);
            summaryMsg.setContent("以下是之前对话的摘要：\n" + summary);
            result.add(summaryMsg);
            result.addAll(recentMessages);

            log.info("[Chat][Summary] 上下文摘要完成: 原始{}条({}KB) → 摘要+最近{}条",
                    history.size(), String.format("%.1f", totalKb), keepRecent);
            return result;
        } catch (Exception e) {
            log.warn("[Chat][Summary] 摘要生成失败，使用原始上下文: {}", e.getMessage());
            return history;
        }
    }

    /**
     * 持久化消息并更新会话统计（含metadata和tokenCount）
     */
    public void saveMessage(Long sessionId, MessageRole role, String content, String metadata, int tokenCount) {
        Message msg = new Message();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setContentType(ContentType.TEXT);
        msg.setTokenCount(tokenCount);
        msg.setMetadata(metadata);
        messageMapper.insert(msg);
        chatSessionService.updateStats(sessionId, tokenCount);
    }

    /**
     * 持久化消息（无metadata）
     */
    public void saveMessage(Long sessionId, MessageRole role, String content) {
        saveMessage(sessionId, role, content, null, 0);
    }
}

package com.lightbot.service.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.constant.ChatAttachmentConstants;
import com.lightbot.constant.ConfigKeys;
import com.lightbot.common.BizException;
import com.lightbot.dto.ChatAttachmentDTO;
import com.lightbot.dto.ChatRequest;
import com.lightbot.enums.ErrorCode;
import com.lightbot.dto.AgentChatCapabilitiesDTO;
import com.lightbot.util.AgentChatCapabilitiesUtil;
import com.lightbot.util.ChatDocumentMessageUtil;
import com.lightbot.util.ChatMessageMediaUtil;
import com.lightbot.util.LlmTraceContext;
import com.lightbot.util.MinioUtil;
import com.lightbot.util.PromptTemplateUtil;
import com.lightbot.entity.Agent;
import com.lightbot.entity.Message;
import com.lightbot.enums.ContentType;
import com.lightbot.enums.MessageRole;
import com.lightbot.enums.MessageType;
import com.lightbot.mapper.MessageMapper;
import com.lightbot.model.ModelFactory;
import com.lightbot.model.ProviderResolver;
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
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
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
    private final ProviderResolver providerResolver;
    private final MinioUtil minioUtil;
    private final ObjectMapper objectMapper;

    /** 平台统一回复约束（追加到所有 Agent 系统提示词后） */
    private static final String PLATFORM_REPLY_CONSTRAINTS = """

            ## 知识库检索后如何回答（重要）
            - 调用 query_knowledge 等检索工具后，**用自己的话概括、总结**检索结果，禁止大段照搬原文
            - 只提炼与**当前用户问题**直接相关的要点；可简要说明来源，无需粘贴全文
            - 检索内容较多时：先给 1–2 句结论，再用 3–5 条短列表列要点
            - 可在文末补充：「如需了解【某主题】的更多细节，可以继续问我」

            ## 篇幅与表达
            - 简单、明确的问题：1–3 句话直接回答，避免客套空话和重复铺垫
            - 一般回答建议控制在约 **150–400 字**；除非用户明确要求「详细说明」「完整列出」「逐条解释」，否则不写长文
            - 复杂问题可用小标题 + 短列表，避免连续多个大段落
            - 遇到不确定的信息请如实告知

            ## 对话聚焦
            - **只回答当前用户最后一条消息中的问题**；历史消息仅作背景，勿复述无关旧话题
            """;

    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是 LightBot 智能助手。请根据用户的提问，利用可用的工具来提供准确、清晰的回答。

            ## 工具使用原则
            - 当工具返回了检索结果时，必须基于这些结果回答，但须**总结归纳**，不要原文复述
            - 工具返回"未找到相关内容"时，再基于自身知识回答，并说明知识库中未找到
            - 有参考文献时，可在回答末尾简要标注来源（文档名即可）

            ## 回答规范
            - 使用中文回答
            - 优先简洁、准确、可读

            ## 输出格式
            - 使用 Markdown 格式输出
            - 多个要点时使用列表（- 或 1.）
            - 数据对比使用表格
            - 重点内容使用 **加粗** 标记
            """;

    @Override
    public Flux<String> execute(ChatContext ctx, ChatMiddlewareChain next) {
        validateAttachments(ctx.getRequest().getAttachments(), ctx.getConfigMap());
        String userText = resolveUserText(ctx.getRequest());
        if (Boolean.TRUE.equals(ctx.getRequest().getRegenerate())) {
            deleteLastAssistantMessage(ctx.getSessionId());
            // 编辑重发：更新用户消息内容
            if (ctx.getRequest().getEditMessageId() != null) {
                updateUserMessageContent(ctx.getRequest().getEditMessageId(), userText);
            }
        } else {
            // 检测 ask_user 父消息（在保存前执行，因为保存后当前消息变成最后一条）
            Long askUserParentId = detectAskUserParentId(ctx.getSessionId());
            Long userMsgId = saveUserMessage(ctx.getSessionId(), userText, ctx.getRequest().getAttachments(), askUserParentId);
            ctx.setUserMessageId(userMsgId);
            if (askUserParentId != null) {
                ctx.setUserMessageParentId(askUserParentId);
            }
        }

        List<org.springframework.ai.chat.messages.Message> messages = buildMessages(
                ctx.getSessionId(), userText, ctx.getAgent(), ctx.getRequest(), ctx.getConfigMap(), ctx);
        ctx.setMessages(messages);

        return next.proceed(ctx);
    }

    /**
     * 同步路径专用：保存用户消息 + 构建消息列表
     */
    public void prepare(ChatContext ctx) {
        validateAttachments(ctx.getRequest().getAttachments(), ctx.getConfigMap());
        String userText = resolveUserText(ctx.getRequest());
        saveUserMessage(ctx.getSessionId(), userText, ctx.getRequest().getAttachments(), null);
        List<org.springframework.ai.chat.messages.Message> messages = buildMessages(
                ctx.getSessionId(), userText, ctx.getAgent(), ctx.getRequest(), ctx.getConfigMap(), ctx);
        ctx.setMessages(messages);
    }

    /**
     * 校验附件数量与类型：文档需开启文件读取；图片/视频需开启多模态对应能力
     */
    private void validateAttachments(List<ChatAttachmentDTO> attachments, Map<String, Object> configMap) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        if (attachments.size() > ChatAttachmentConstants.MAX_ATTACHMENTS_PER_MESSAGE) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                    "单条消息最多上传 " + ChatAttachmentConstants.MAX_ATTACHMENTS_PER_MESSAGE + " 个附件");
        }
        AgentChatCapabilitiesDTO caps = AgentChatCapabilitiesUtil.fromConfigMap(configMap);
        for (ChatAttachmentDTO att : attachments) {
            if (att == null || att.getType() == null) {
                continue;
            }
            if (ChatDocumentMessageUtil.isDocumentAttachment(att)) {
                if (!Boolean.TRUE.equals(caps.getEnableFileRead())) {
                    throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "当前 Agent 未开启文件读取");
                }
                if (att.getParsedText() == null || att.getParsedText().isBlank()) {
                    throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "文档附件缺少解析内容，请重新上传");
                }
            } else if ("image".equals(att.getType())) {
                if (!Boolean.TRUE.equals(caps.getAllowMediaUpload())
                        || !Boolean.TRUE.equals(caps.getEnableImageInput())) {
                    throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "当前 Agent 未开启图像输入");
                }
            } else if ("video".equals(att.getType())) {
                if (!Boolean.TRUE.equals(caps.getAllowMediaUpload())
                        || !Boolean.TRUE.equals(caps.getEnableVideoInput())) {
                    throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "当前 Agent 未开启视频输入");
                }
            } else {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "不支持的附件类型: " + att.getType());
            }
        }
        ChatDocumentMessageUtil.validateMediaMix(attachments);
    }

    private String resolveUserText(ChatRequest request) {
        String msg = request.getMessage();
        if (msg != null && !msg.isBlank()) {
            return msg.trim();
        }
        if (request.getAttachments() != null && !request.getAttachments().isEmpty()) {
            return "请根据附件内容回答。";
        }
        throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "消息不能为空");
    }

    private Long saveUserMessage(Long sessionId, String content, List<ChatAttachmentDTO> attachments, Long parentId) {
        // 检测是否有图片附件 → messageType 为 MULTIMODAL_IMAGE
        boolean hasImage = attachments != null && attachments.stream()
                .anyMatch(att -> "image".equals(att.getType()));
        MessageType messageType = hasImage ? MessageType.MULTIMODAL_IMAGE : MessageType.TEXT;

        if (attachments == null || attachments.isEmpty()) {
            return saveMessage(sessionId, MessageRole.USER, content, null, 0, messageType, parentId);
        }
        try {
            String metadata = objectMapper.writeValueAsString(Map.of("attachments", attachments));
            return saveMessage(sessionId, MessageRole.USER, content, metadata, 0, messageType, parentId);
        } catch (Exception e) {
            return saveMessage(sessionId, MessageRole.USER, content, null, 0, messageType, parentId);
        }
    }

    /**
     * 检测 ask_user 父消息：查找会话中最后一条助手消息，
     * 如果其 metadata 包含 ask_user 工具调用事件，则返回该消息ID作为 parentId
     *
     * @return 父消息ID，无则返回 null
     */
    private Long detectAskUserParentId(Long sessionId) {
        Message lastAssistant = messageMapper.selectOne(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getSessionId, sessionId)
                        .eq(Message::getRole, MessageRole.ASSISTANT)
                        .orderByDesc(Message::getCreateTime)
                        .last("LIMIT 1"));
        if (lastAssistant == null || lastAssistant.getMetadata() == null) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = objectMapper.readValue(lastAssistant.getMetadata(), Map.class);
            Object toolEvents = meta.get("toolEvents");
            if (toolEvents instanceof List<?> events) {
                boolean hasAskUser = events.stream()
                        .filter(e -> e instanceof Map)
                        .anyMatch(e -> "ask_user".equals(((Map<?, ?>) e).get("toolName")));
                if (hasAskUser) {
                    return lastAssistant.getId();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 构建消息列表：系统提示词 + 工具使用引导 + 历史消息 + 当前用户消息
     */
    private List<org.springframework.ai.chat.messages.Message> buildMessages(
            Long sessionId, String userMessage, Agent agent, ChatRequest request,
            Map<String, Object> agentConfigMap, ChatContext ctx) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();

        // 1. 使用已解析的 Agent 配置（含版本选择），获取上下文条数
        if (agentConfigMap == null) {
            agentConfigMap = agent != null ? initMiddleware.resolveRuntimeConfigMap(agent, request) : Map.of();
        }
        int maxContextMessages = 20;
        if (agentConfigMap.containsKey("maxContextMessages")) {
            Object v = agentConfigMap.get("maxContextMessages");
            maxContextMessages = v instanceof Number ? ((Number) v).intValue() : Integer.parseInt(v.toString());
        }

        // 2. 系统提示词：优先使用Agent的systemPrompt（放在最前面确保最高优先级）
        String systemPrompt = (agent != null && agent.getSystemPrompt() != null && !agent.getSystemPrompt().isBlank())
                ? "# 核心指令（最高优先级，以下所有规则不得覆盖此处内容）\n\n" + agent.getSystemPrompt()
                : DEFAULT_SYSTEM_PROMPT;

        // 3. 如果Agent绑定了工具或Skill，追加工具使用引导到系统提示词
        if (agent != null) {
            // 3.1 工具引导：合并 Agent 自身绑定的工具 + Skill 引入的额外工具
            // 优先使用版本快照中的绑定 ID，避免暂存/发布混淆
            List<Long> baseToolIds = ctx != null && ctx.getVersionToolIds() != null
                    ? ctx.getVersionToolIds() : agentService.getToolIds(agent.getId());
            List<Long> toolIds = new java.util.ArrayList<>(baseToolIds);
            if (ctx != null && ctx.getSkillExtraToolIds() != null) {
                for (Long id : ctx.getSkillExtraToolIds()) {
                    if (id != null && !toolIds.contains(id)) toolIds.add(id);
                }
            }
            if (!toolIds.isEmpty()) {
                List<ToolCallback> toolCallbacks = toolService.resolveToolCallbacksByIds(toolIds);
                if (!toolCallbacks.isEmpty()) {
                    // Agent 系统提示词保持在最前面（最高优先级），工具引导追加在后
                    systemPrompt = systemPrompt + "\n\n" + buildToolGuide(toolCallbacks, agentConfigMap);
                }
            }

            // 3.2 Skill 提示词追加块
            if (ctx != null && ctx.getSkillSystemAppendix() != null
                    && !ctx.getSkillSystemAppendix().isBlank()) {
                systemPrompt = systemPrompt + ctx.getSkillSystemAppendix();
            }
        }

        // 3.1 替换提示词中的 {{变量}}：默认值 + biz_params 入参
        Object promptVarDefs = agentConfigMap.get(ConfigKeys.Agent.PROMPT_VARIABLES);
        Map<String, Object> bizParams = request != null && request.getBizParams() != null
                ? request.getBizParams() : Map.of();
        Map<String, Object> varValues = PromptTemplateUtil.mergeVariableValues(promptVarDefs, bizParams);
        systemPrompt = PromptTemplateUtil.render(systemPrompt, varValues);
        systemPrompt = systemPrompt + PLATFORM_REPLY_CONSTRAINTS;

        messages.add(new org.springframework.ai.chat.messages.SystemMessage(systemPrompt));

        // 4. 加载历史消息：必须按「最近 N 条」取，再按时间正序交给模型（原先 ASC LIMIT 会取最旧 N 条，长会话会丢当前上下文、模型易被旧问题带偏）
        List<Message> history = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getSessionId, sessionId)
                        .orderByDesc(Message::getCreateTime)
                        .last("LIMIT " + (maxContextMessages + 1)));
        Collections.reverse(history);

        // 排除最后一条如果就是当前用户消息（已在上面先持久化）
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
                List<ChatAttachmentDTO> histAttachments = parseAttachmentsFromMetadata(msg.getMetadata());
                messages.add(buildUserMessageForAttachments(msg.getContent(), histAttachments));
            } else if (msg.getRole() == MessageRole.ASSISTANT) {
                messages.add(new AssistantMessage(msg.getContent()));
            } else if (msg.getRole() == MessageRole.SYSTEM && msg.getContent() != null && !msg.getContent().isBlank()) {
                messages.add(new SystemMessage(msg.getContent()));
            }
        }

        // 6. 当前用户消息（文档走文本注入，图片/视频走多模态）
        List<ChatAttachmentDTO> attachments = request != null ? request.getAttachments() : null;
        messages.add(buildUserMessageForAttachments(userMessage, attachments));
        return messages;
    }

    /**
     * 构建用户消息：document 附件拼入文本，image/video 仍走多模态
     */
    private org.springframework.ai.chat.messages.Message buildUserMessageForAttachments(
            String content, List<ChatAttachmentDTO> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return new UserMessage(content != null ? content : "");
        }
        // 文档 → Tika 文本拼入提示词；图片/视频 → 多模态一并发送（可混合）
        List<ChatAttachmentDTO> documents = ChatDocumentMessageUtil.filterDocuments(attachments);
        List<ChatAttachmentDTO> media = ChatDocumentMessageUtil.filterMedia(attachments);
        String text = ChatDocumentMessageUtil.wrapUserMessage(content, documents);
        if (!media.isEmpty()) {
            return ChatMessageMediaUtil.buildUserMessage(text, media, minioUtil);
        }
        return new UserMessage(text);
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
            // 输出参数 schema，避免 AI 猜测参数名
            appendParamSchema(sb, cb.getToolDefinition().inputSchema());
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
                3. 工具返回的结果须**概括总结后**写入回答，不得大段复制粘贴工具原文
                4. 若调用了 query_knowledge：先给简短结论，再列要点；内容多时可提示用户「如需某部分细节可继续问我」
                5. 如果工具返回"未找到相关内容"，请基于自身知识回答并说明知识库中未找到相关信息
                6. 禁止在工具尚未返回结果时就提前结束对话
                7. **重要：只根据当前用户的问题来决定调用哪些工具，不要根据历史对话中的无关内容来调用工具**
                   - 历史对话中提到的实体/主题，如果与当前问题无关，不要主动查询
                   - 例如：历史中提到了"A公司"，但当前问题问"B是谁"，只查询"B"，不要查询"A公司"
                """);

        return sb.toString();
    }

    /**
     * 解析工具 inputSchema 并输出参数列表到引导文本
     */
    private void appendParamSchema(StringBuilder sb, String inputSchema) {
        if (inputSchema == null || inputSchema.isBlank()) {
            return;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(inputSchema);
            com.fasterxml.jackson.databind.JsonNode props = root.get("properties");
            if (props == null || props.isEmpty()) {
                return;
            }
            java.util.Set<String> required = new java.util.HashSet<>();
            com.fasterxml.jackson.databind.JsonNode reqNode = root.get("required");
            if (reqNode != null && reqNode.isArray()) {
                reqNode.forEach(n -> required.add(n.asText()));
            }
            sb.append("  参数：\n");
            props.fields().forEachRemaining(entry -> {
                String name = entry.getKey();
                com.fasterxml.jackson.databind.JsonNode prop = entry.getValue();
                String type = prop.has("type") ? prop.get("type").asText() : "string";
                String desc = prop.has("description") ? prop.get("description").asText() : "";
                String reqMark = required.contains(name) ? "（必填）" : "（选填）";
                sb.append("  - `").append(name).append("` (").append(type).append(")").append(reqMark);
                if (!desc.isBlank()) {
                    sb.append(" — ").append(desc);
                }
                sb.append("\n");
            });
        } catch (Exception ignored) {
            // schema 解析失败不影响工具引导生成
        }
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

        // 从配置读取摘要后保留消息数，默认 6
        int keepRecent = 6;
        Object keepVal = configMap.get(ConfigKeys.Agent.SUMMARY_KEEP_MESSAGES);
        if (keepVal instanceof Number kn) {
            keepRecent = Math.max(1, Math.min(kn.intValue(), 50));
        }

        if (history.size() <= keepRecent + 2) {
            return history;
        }

        List<Message> olderMessages = history.subList(0, history.size() - keepRecent);
        List<Message> recentMessages = history.subList(history.size() - keepRecent, history.size());

        try {
            Long providerId = providerResolver.resolveFromConfig(configMap);
            ChatModel chatModel = modelFactory.getChatModel(providerId);

            // 从配置读取工具结果预览 Token 上限，默认 500
            int toolResultTokenLimit = 500;
            Object tokenLimitVal = configMap.get(ConfigKeys.Agent.SUMMARY_TOOL_RESULT_TOKEN_LIMIT);
            if (tokenLimitVal instanceof Number tln) {
                toolResultTokenLimit = Math.max(50, Math.min(tln.intValue(), 5000));
            }
            int toolResultCharLimit = toolResultTokenLimit * 4;

            StringBuilder conversationText = new StringBuilder();
            for (Message msg : olderMessages) {
                String role = msg.getRole() == MessageRole.USER ? "用户" : "助手";
                String content = msg.getContent() != null ? msg.getContent() : "";
                // 工具结果截断预览
                if (msg.getRole() == MessageRole.TOOL && content.length() > toolResultCharLimit) {
                    content = content.substring(0, toolResultCharLimit) + "\n[已截断，共" + content.length() + "字符]";
                }
                conversationText.append(role).append("：").append(content).append("\n");
            }

            // 从配置读取摘要提示词，默认使用内置 prompt
            String summaryPromptText = "你是一个对话摘要助手。请将以下对话内容压缩为简明摘要，保留关键信息、决策和上下文要点。只输出摘要，不要添加额外说明。";
            Object customPrompt = configMap.get(ConfigKeys.Agent.SUMMARY_PROMPT);
            if (customPrompt instanceof String cp && !cp.isBlank()) {
                summaryPromptText = cp;
            }

            List<org.springframework.ai.chat.messages.Message> summaryMessages = new ArrayList<>();
            summaryMessages.add(new SystemMessage(summaryPromptText));
            summaryMessages.add(new UserMessage("请对以下对话进行摘要：\n\n" + conversationText));

            ChatResponse response = LlmTraceContext.callWithoutTrace(() -> chatModel.call(new Prompt(summaryMessages)));
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
     *
     * @return 消息ID
     */
    public Long saveMessage(Long sessionId, MessageRole role, String content, String metadata, int tokenCount) {
        return saveMessage(sessionId, role, content, metadata, tokenCount, MessageType.TEXT, null);
    }

    /**
     * 持久化消息（含 messageType 和 parentId）
     *
     * @return 消息ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long saveMessage(Long sessionId, MessageRole role, String content, String metadata,
                            int tokenCount, MessageType messageType, Long parentId) {
        Message msg = new Message();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setContentType(ContentType.TEXT);
        msg.setMessageType(messageType != null ? messageType : MessageType.TEXT);
        msg.setTokenCount(tokenCount);
        msg.setMetadata(metadata);
        msg.setParentId(parentId);
        messageMapper.insert(msg);
        chatSessionService.updateStats(sessionId, tokenCount);
        return msg.getId();
    }

    /**
     * 持久化消息（无metadata）
     *
     * @return 消息ID
     */
    public Long saveMessage(Long sessionId, MessageRole role, String content) {
        return saveMessage(sessionId, role, content, null, 0, MessageType.TEXT, null);
    }

    /**
     * 重新生成：删除会话中最近一条助手消息（及统计）
     */
    public void deleteLastAssistantMessage(Long sessionId) {
        Message last = messageMapper.selectOne(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getSessionId, sessionId)
                        .eq(Message::getRole, MessageRole.ASSISTANT)
                        .orderByDesc(Message::getCreateTime)
                        .last("LIMIT 1"));
        if (last == null) {
            return;
        }
        messageMapper.deleteById(last.getId());
        var session = chatSessionService.getById(sessionId);
        if (session != null && session.getMessageCount() != null && session.getMessageCount() > 0) {
            session.setMessageCount(session.getMessageCount() - 1);
            int tokens = last.getTokenCount() != null ? last.getTokenCount() : 0;
            if (tokens > 0 && session.getTotalTokens() != null) {
                session.setTotalTokens(Math.max(0L, session.getTotalTokens() - tokens));
            }
            chatSessionService.updateById(session);
        }
    }

    /**
     * 编辑重发：更新用户消息内容
     */
    public void updateUserMessageContent(Long messageId, String newContent) {
        Message msg = messageMapper.selectById(messageId);
        if (msg != null && msg.getRole() == MessageRole.USER) {
            msg.setContent(newContent);
            messageMapper.updateById(msg);
        }
    }

    /**
     * 从消息 metadata 解析用户附件列表
     */
    @SuppressWarnings("unchecked")
    private List<ChatAttachmentDTO> parseAttachmentsFromMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return List.of();
        }
        try {
            Map<String, Object> meta = objectMapper.readValue(metadata, new TypeReference<>() {});
            Object raw = meta.get("attachments");
            if (raw instanceof List<?> list) {
                List<ChatAttachmentDTO> result = new ArrayList<>();
                for (Object item : list) {
                    result.add(objectMapper.convertValue(item, ChatAttachmentDTO.class));
                }
                return result;
            }
        } catch (Exception e) {
            log.debug("[Chat] 解析消息附件 metadata 失败: {}", e.getMessage());
        }
        return List.of();
    }
}

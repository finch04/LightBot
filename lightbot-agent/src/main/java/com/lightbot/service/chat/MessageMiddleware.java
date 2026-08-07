package com.lightbot.service.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.constant.ChatAttachmentConstants;
import com.lightbot.constant.ConfigKeys;
import com.lightbot.common.BizException;
import com.lightbot.dto.ChatAttachmentDTO;
import com.lightbot.dto.ChatMentionDTO;
import com.lightbot.dto.ChatMentionDTO;
import com.lightbot.dto.ChatRequestDTO;
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
import com.lightbot.service.ChatAttachmentParsedService;
import com.lightbot.service.AgentService;
import com.lightbot.service.ChatSessionService;
import com.lightbot.service.SessionTodoService;
import com.lightbot.service.LongMemoryPolicyService;
import com.lightbot.service.UserMemoryService;
import com.lightbot.vo.LongMemoryPolicyVO;
import com.lightbot.vo.TodoItemVO;
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
import java.util.LinkedHashMap;
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
    private final ChatSessionService chatSessionService;
    private final ModelFactory modelFactory;
    private final InitMiddleware initMiddleware;
    private final ProviderResolver providerResolver;
    private final MinioUtil minioUtil;
    private final ObjectMapper objectMapper;
    private final ChatAttachmentParsedService chatAttachmentParsedService;
    private final UserMemoryService userMemoryService;
    private final LongMemoryPolicyService longMemoryPolicyService;
    /** 用于把当前会话 todos 快照注入 system prompt，避免 AI 在长调研中漏传导致丢项 */
    private final SessionTodoService sessionTodoService;
    /** 用于生成 SSE 状态事件 JSON（context_compression 等） */
    private final ToolEventGenerator toolEventGenerator;

    /** 平台统一回复约束（不含工具相关，工具能力由 Provider 决定后按需追加） */
    private static final String PLATFORM_REPLY_CONSTRAINTS = """

            ## 篇幅与表达
            - 简单、明确的问题：1–3 句话直接回答，避免客套空话和重复铺垫
            - 一般回答建议控制在约 **150–400 字**；除非用户明确要求「详细说明」「完整列出」「逐条解释」，否则不写长文
            - 复杂问题可用小标题 + 短列表，避免连续多个大段落
            - 遇到不确定的信息请如实告知

            ## Markdown 格式规范（重要）
            - 表格必须包含分隔行：表头下方必须有 `| --- |` 分隔行，否则表格无法渲染
            - 表格每行必须以 `|` 开头和结尾，列之间用 `|` 分隔
            - **加粗** 和 *斜体* 标记必须成对出现，不要遗漏闭合符号
            - 列表项之间不要插入空行（否则会被解析为独立段落）
            - 代码块必须使用 ``` 包裹，不要缩进混用

            ## HTML 可视化（生成可交互页面时必须遵守）
            - 当用户要求「预览页面 / 做一个 demo / 可视化 / 交互组件」等需要浏览器渲染 HTML 的场景，
              必须用 ```html:preview 围栏代码块输出**完整可直接运行的 HTML 文档**，平台会在沙盒 iframe 中渲染
            - 不要把 HTML 放在普通 ```html 代码块里（用户看到的是源码而非渲染结果）
            - HTML 内可以写 `<style>` 与 `<script>`；iframe 沙盒只放开 allow-scripts，禁止访问父窗口或外发请求
            - 同一轮回答只输出一个 html:preview 块，必要时把说明文字写在围栏之外

            ## 对话聚焦
            - **只回答当前用户最后一条消息中的问题**；历史消息仅作背景，勿复述无关旧话题
            """;

    /** 模型支持 API 工具调用时追加：知识库检索回答规范 */
    private static final String PLATFORM_TOOL_KNOWLEDGE_HINT = """

            ## 知识库检索后如何回答（重要）
            - 调用 query_knowledge 等检索工具后，**用自己的话概括、总结**检索结果，禁止大段照搬原文
            - 只提炼与**当前用户问题**直接相关的要点；可简要说明来源，无需粘贴全文
            - 检索内容较多时：先给 1–2 句结论，再用 3–5 条短列表列要点
            - 可在文末补充：「如需了解【某主题】的更多细节，可以继续问我」
            """;

    /** 工具调用轮次预算提示（%d 为运行时 maxExecutionSteps） */
    private static final String TOOL_STEP_BUDGET_HINT_TEMPLATE = """

            ## 工具调用轮次预算（重要）
            - 本轮对话「模型 ↔ 工具」交互总轮次上限约为 **%d 步**（一次工具调用或一次模型回复计 1 步），超出后平台会强制停止工具调用并直接返回。
            - 达到目标即刻停止调用工具，切换为文字回复；不要反复调用同一工具、也不要为了"更完整"而无节制地检索。
            - 若剩余预算已接近上限（约 3~5 步以内），应停止继续调用工具，基于当前已有信息直接给出结论，并明确标注证据缺口或不确定性。
            - 每次调用前先问自己：这一步是否真的必要？能不能合并到一次调用完成？
            """;

    /**
     * 仅在当前 Agent 实际绑定了 SubAgent 时附加。它描述编排顺序而不重复工具 schema，
     * 因此不会重新引入“将全部工具拼到提示词”的性能问题。
     */
    private static final String SUBAGENT_COLLABORATION_PROTOCOL = """

            ## 多智能体协作
            - 仅当问题确实需要拆分为多个相互独立的调研/分析子任务时，才使用子智能体；简单问题请直接完成，避免为了协作而协作。
            - 若范围、时间、地区、交付形式等缺失会实质改变调研结论，且 `ask_user` 可用，先一次性用 1~3 个问题澄清；问题应具体、可选，不要追问可合理假定的细节。调用后必须等待用户回复，不能继续委派或调用其他工具。
            - 需要协作时，先调用 `write_todos` 写入 2~8 项可验收的**完整**待办快照，将一个待办标为 `in_progress`；随后使用一次 `delegate_to_subagent`，把互不依赖的子任务放入 `tasks` 并指定 `mode="parallel"`。仅支持 `sync`、`parallel`；父 Agent 会等待子任务返回最终结果，并在拿到结果后继续本轮生成、归纳和交付。
            - 子任务描述必须包含研究目标、必要上下文、期望交付和证据要求；不要重复委派同一问题，也不要把最终汇总交给子智能体。关键数字、核心结论或冲突来源应由独立来源或合适的核验子智能体复核。
            - 收到各子智能体结果后，基于结果更新同一份完整待办快照；只有确有依赖关系时才在前一批完成后继续委派。全部待办进入终态后，由主 Agent 综合结论、说明未核验/失败项，并回复用户。
            - 每个子任务必须目标明确、可独立验收；不要把同一问题重复委派给多个子智能体，也不要把主 Agent 的最终汇总工作交给子智能体。
            """;

    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是智元智能助手。请根据用户的提问，利用可用的工具来提供准确、清晰的回答。

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
            - 数据对比使用表格，**表头下方必须有 `| --- |` 分隔行**
            - 重点内容使用 **加粗** 标记
            - 表格每行以 `|` 开头和结尾，加粗/斜体标记必须成对闭合
            """;

    private static final String DEFAULT_SYSTEM_PROMPT_NO_TOOLS = """
            你是智元智能助手。请根据用户的提问提供准确、清晰的回答。

            ## 回答规范
            - 使用中文回答
            - 优先简洁、准确、可读
            - 遇到不确定的信息请如实告知

            ## 输出格式
            - 使用 Markdown 格式输出
            - 多个要点时使用列表（- 或 1.）
            - 数据对比使用表格，**表头下方必须有 `| --- |` 分隔行**
            - 重点内容使用 **加粗** 标记
            - 表格每行以 `|` 开头和结尾，加粗/斜体标记必须成对闭合
            """;

    @Override
    public Flux<String> execute(ChatContext ctx, ChatMiddlewareChain next) {
        validateAttachments(ctx.getRequest().getAttachments(), ctx.getConfigMap(), ctx.getSessionId());
        String userText = resolveUserText(ctx.getRequest());
        if (Boolean.TRUE.equals(ctx.getRequest().getRegenerate())) {
            // 指定 ID 时删除对应助手消息（已落库的失败/成功回复）；未指定则跳过（未落库重试）
            Long deleteId = ctx.getRequest().getDeleteAssistantMessageId();
            if (deleteId != null) {
                deleteAssistantMessageById(ctx.getSessionId(), deleteId);
            }
            if (ctx.getRequest().getEditMessageId() != null) {
                // 编辑重发：更新用户消息内容
                updateUserMessageContent(ctx.getRequest().getEditMessageId(), userText);
            }
        } else {
            // 检测 ask_user 父消息（在保存前执行，因为保存后当前消息变成最后一条）
            Long askUserParentId = detectAskUserParentId(ctx.getSessionId());
            // 校验引用回复目标：必须存在且属于同一会话
            Long replyToId = ctx.getRequest().getReplyToMessageId();
            if (replyToId != null) {
                validateReplyToMessage(replyToId, ctx.getSessionId());
            }
            Long userMsgId = saveUserMessage(ctx.getSessionId(), userText, ctx.getRequest().getAttachments(), askUserParentId, replyToId,
                    ctx.getRequest().getMentions(), ctx.getRequest().getAgentVersionId(), ctx.getConfigMap(), ctx.getRequestId());
            ctx.setUserMessageId(userMsgId);
            if (askUserParentId != null) {
                ctx.setUserMessageParentId(askUserParentId);
            }
        }

        List<org.springframework.ai.chat.messages.Message> messages = buildMessages(
                ctx.getSessionId(), userText, ctx.getAgent(), ctx.getRequest(), ctx.getConfigMap(), ctx);
        ctx.setMessages(messages);

        // 取出 buildMessages 期间累积的 pre-stream 状态事件（context_compression 等），
        // prepend 到 next.proceed 之前下发，确保前端在 LLM 输出到达前收到占位提示
        List<String> preStreamEvents = ctx.getPreStreamStatusEvents();
        if (preStreamEvents == null || preStreamEvents.isEmpty()) {
            return next.proceed(ctx);
        }
        return Flux.fromIterable(new ArrayList<>(preStreamEvents))
                .concatWith(next.proceed(ctx));
    }

    /**
     * 同步路径专用：保存用户消息 + 构建消息列表
     */
    public void prepare(ChatContext ctx) {
        validateAttachments(ctx.getRequest().getAttachments(), ctx.getConfigMap(), ctx.getSessionId());
        String userText = resolveUserText(ctx.getRequest());
        saveUserMessage(ctx.getSessionId(), userText, ctx.getRequest().getAttachments(), null, null,
                ctx.getRequest().getMentions(), ctx.getRequest().getAgentVersionId(), ctx.getConfigMap(), ctx.getRequestId());
        List<org.springframework.ai.chat.messages.Message> messages = buildMessages(
                ctx.getSessionId(), userText, ctx.getAgent(), ctx.getRequest(), ctx.getConfigMap(), ctx);
        ctx.setMessages(messages);
    }

    /**
     * 保存用户消息（含附件/mentions/requestId 元数据），并写回 ctx.userMessageId。
     * <p>供 UserSensitiveMiddleware 在敏感词短路时复用，保持与正常流程一致的落库语义
     * （含 ask_user 父消息检测、引用回复校验等）</p>
     *
     * @param ctx 对话上下文
     * @return 用户消息 ID
     */
    public Long persistUserMessage(ChatContext ctx) {
        String userText = resolveUserText(ctx.getRequest());
        Long askUserParentId = detectAskUserParentId(ctx.getSessionId());
        Long replyToId = ctx.getRequest().getReplyToMessageId();
        if (replyToId != null) {
            validateReplyToMessage(replyToId, ctx.getSessionId());
        }
        Long userMsgId = saveUserMessage(ctx.getSessionId(), userText, ctx.getRequest().getAttachments(),
                askUserParentId, replyToId, ctx.getRequest().getMentions(),
                ctx.getRequest().getAgentVersionId(), ctx.getConfigMap(), ctx.getRequestId());
        ctx.setUserMessageId(userMsgId);
        if (askUserParentId != null) {
            ctx.setUserMessageParentId(askUserParentId);
        }
        return userMsgId;
    }

    /**
     * 校验附件数量与类型：文档需开启文件读取；图片/视频需开启多模态对应能力
     */
    private void validateAttachments(List<ChatAttachmentDTO> attachments, Map<String, Object> configMap, Long sessionId) {
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
                // 无解析产物（加密/扫描件等）不阻断发送，wrapUserMessage 会以占位文本安全降级
            } else if ("image".equals(att.getType())) {
                // 图片放开：多模态 Agent 直喂模型，非多模态 Agent 交由 OCR 工具识别；仅需允许上传附件
                if (!Boolean.TRUE.equals(caps.getAllowFileUpload())) {
                    throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "当前 Agent 未开启附件上传");
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

    private String resolveUserText(ChatRequestDTO request) {
        String msg = request.getMessage();
        if (msg != null && !msg.isBlank()) {
            return msg.trim();
        }
        if (request.getAttachments() != null && !request.getAttachments().isEmpty()) {
            return "请根据附件内容回答。";
        }
        throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "消息不能为空");
    }

    private Long saveUserMessage(Long sessionId, String content, List<ChatAttachmentDTO> attachments,
                                 Long parentId, Long replyToMessageId,
                                 List<ChatMentionDTO> mentions, Long agentVersionId,
                                 Map<String, Object> configMap, String requestId) {
        // 图片附件仅在多模态图像输入开启时标记为 MULTIMODAL_IMAGE；OCR 兜底图片按 TEXT 处理
        boolean enableImageInput = Boolean.TRUE.equals(
                AgentChatCapabilitiesUtil.fromConfigMap(configMap).getEnableImageInput());
        boolean hasImage = enableImageInput && attachments != null && attachments.stream()
                .anyMatch(att -> "image".equals(att.getType()));
        MessageType messageType = hasImage ? MessageType.MULTIMODAL_IMAGE : MessageType.TEXT;

        boolean hasAttachments = attachments != null && !attachments.isEmpty();
        boolean hasMentions = mentions != null && !mentions.isEmpty();

        // requestId 使输入、助手消息和子任务统一归属于同一轮请求。
        if (!hasAttachments && !hasMentions && (requestId == null || requestId.isBlank())) {
            return saveMessage(sessionId, MessageRole.USER, content, null, 0, messageType, parentId, replyToMessageId);
        }
        try {
            // 先将附件迁移到 sessions/{sessionId}/inputs/ 下，确保消息 metadata 与索引都引用会话内路径
            if (hasAttachments) {
                chatSessionService.relocateAttachmentsToSession(sessionId, attachments);
            }
            Map<String, Object> metaMap = new LinkedHashMap<>();
            if (hasAttachments) {
                metaMap.put("attachments", attachments);
            }
            if (hasMentions) {
                metaMap.put("mentions", buildMentionSnapshots(mentions, agentVersionId));
            }
            if (requestId != null && !requestId.isBlank()) {
                metaMap.put("requestId", requestId);
            }
            String metadata = objectMapper.writeValueAsString(metaMap);
            Long msgId = saveMessage(sessionId, MessageRole.USER, content, metadata, 0, messageType, parentId, replyToMessageId);
            // 同步追加附件到会话级上下文
            if (hasAttachments) {
                chatSessionService.appendSessionAttachments(sessionId, attachments, "user_upload");
            }
            return msgId;
        } catch (Exception e) {
            return saveMessage(sessionId, MessageRole.USER, content, null, 0, messageType, parentId, replyToMessageId);
        }
    }

    /**
     * 构建 mention 持久化快照：保留 type/resourceId/name/token/agentVersionId，
     * 供历史消息回显使用（不依赖实时资源列表，资源失效也能展示 token）
     */
    private List<Map<String, Object>> buildMentionSnapshots(List<ChatMentionDTO> mentions, Long agentVersionId) {
        String avIdStr = agentVersionId != null ? agentVersionId.toString() : null;
        List<Map<String, Object>> snapshots = new ArrayList<>(mentions.size());
        for (ChatMentionDTO m : mentions) {
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("type", m.getType() != null ? m.getType().getCode() : null);
            snap.put("resourceId", m.getResourceId());
            snap.put("name", m.getName());
            snap.put("token", m.getToken());
            snap.put("agentVersionId", avIdStr);
            snapshots.add(snap);
        }
        return snapshots;
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
     * 校验引用回复目标：消息必须存在且属于同一会话
     */
    private void validateReplyToMessage(Long replyToMessageId, Long sessionId) {
        Message target = messageMapper.selectById(replyToMessageId);
        if (target == null || !sessionId.equals(target.getSessionId())) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "引用的消息不存在或不属于当前会话");
        }
    }

    /**
     * 查询被引用消息的内容（截取前 200 字用于 LLM 上下文注入）
     *
     * @return 被引用内容摘要，无则返回 null
     */
    private String resolveReplyToContent(Long replyToMessageId) {
        if (replyToMessageId == null) {
            return null;
        }
        Message target = messageMapper.selectById(replyToMessageId);
        if (target == null || target.getContent() == null) {
            return null;
        }
        String content = target.getContent();
        return content.length() > 200 ? content.substring(0, 200) + "..." : content;
    }

    /**
     * 构建消息列表：系统提示词 + 工具使用引导 + 历史消息 + 当前用户消息
     */
    private List<org.springframework.ai.chat.messages.Message> buildMessages(
            Long sessionId, String userMessage, Agent agent, ChatRequestDTO request,
            Map<String, Object> agentConfigMap, ChatContext ctx) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();

        // 1. 使用已解析的 Agent 配置（含版本选择），获取上下文条数
        if (agentConfigMap == null) {
            agentConfigMap = agent != null ? initMiddleware.resolveRuntimeConfigMap(agent, request) : Map.of();
        }
        int maxContextMessages = ConfigKeys.Agent.DEFAULT_MAX_CONTEXT_MESSAGES;
        if (agentConfigMap.containsKey("maxContextMessages")) {
            Object v = agentConfigMap.get("maxContextMessages");
            maxContextMessages = v instanceof Number ? ((Number) v).intValue() : Integer.parseInt(v.toString());
        }

        Long providerId = ctx != null ? ctx.getProviderId() : providerResolver.resolveFromConfig(agentConfigMap);
        boolean apiToolsEnabled = modelFactory.supportsApiToolCalling(providerId, agentConfigMap);

        // 2. 系统提示词：优先使用Agent的systemPrompt（放在最前面确保最高优先级）
        String systemPrompt;
        if (agent != null && agent.getSystemPrompt() != null && !agent.getSystemPrompt().isBlank()) {
            systemPrompt = "# 核心指令（最高优先级，以下所有规则不得覆盖此处内容）\n\n" + agent.getSystemPrompt();
        } else {
            systemPrompt = apiToolsEnabled ? DEFAULT_SYSTEM_PROMPT : DEFAULT_SYSTEM_PROMPT_NO_TOOLS;
        }

        // 3. 若当前模型支持 API 工具调用，追加工具使用引导
        if (agent != null && apiToolsEnabled) {
            // 3.1 ToolCallback 会由 ToolPrepMiddleware 原生传给模型；禁止重复把工具说明和 schema 拼入 Prompt。

            // 3.2 Skill 提示词追加块
            if (ctx != null && ctx.getSkillSystemAppendix() != null
                    && !ctx.getSkillSystemAppendix().isBlank()) {
                systemPrompt = systemPrompt + ctx.getSkillSystemAppendix();
            }

            // 3.3 @ mention 优先使用提示（不收窄工具加载范围）
            if (ctx != null && ctx.getMentionScope() != null) {
                String mentionAppendix = MentionHintBuilder.buildSystemAppendix(
                        ctx.getMentionScope().getRawMentions());
                if (!mentionAppendix.isBlank()) {
                    systemPrompt = systemPrompt + mentionAppendix;
                }
            }

            // 子智能体属于 Agent 绑定能力，需在消息构建阶段给出轻量编排规则；
            // ToolPrep 仍负责实际注入原生 ToolCallback 与参数 schema。
            if (hasBoundSubAgents(ctx, agent)) {
                systemPrompt = systemPrompt + SUBAGENT_COLLABORATION_PROTOCOL;
            }
        }

        // 3.1 替换提示词中的 {{变量}}：默认值 + biz_params 入参
        Object promptVarDefs = agentConfigMap.get(ConfigKeys.Agent.PROMPT_VARIABLES);
        Map<String, Object> bizParams = request != null && request.getBizParams() != null
                ? request.getBizParams() : Map.of();
        Map<String, Object> varValues = PromptTemplateUtil.mergeVariableValues(promptVarDefs, bizParams);
        systemPrompt = PromptTemplateUtil.render(systemPrompt, varValues);
        systemPrompt = appendUserMemoryPrompt(systemPrompt, ctx, agent, userMessage);
        if (apiToolsEnabled) {
            systemPrompt = systemPrompt + PLATFORM_TOOL_KNOWLEDGE_HINT;
            systemPrompt = systemPrompt + String.format(TOOL_STEP_BUDGET_HINT_TEMPLATE, resolveMaxExecutionStepsHint(agentConfigMap));
        }
        // 注入当前会话 todos 快照：让 AI 调用 write_todos 前看到已有项，避免漏传导致丢项
        systemPrompt = appendCurrentTodosPrompt(systemPrompt, ctx);
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

        // 5. 上下文摘要：在压缩前后向 SSE 通道推送 context_compression 事件，
        // 让前端展示"正在压缩上下文…"占位（避免长会话首次响应延迟被误判为卡死）
        history = summarizeIfNeeded(history, agentConfigMap, agent, ctx);

        // 5.1 处理孤立 USER 消息：AI 回复失败或用户主动停止后，DB 会留下没有 ASSISTANT 配对的 USER 消息
        // 连续 USER 消息会导致 LLM 误判为需要回答多条，在孤立 USER 后插入占位 ASSISTANT 消息
        // 必须在摘要之后执行，避免占位消息被带入摘要
        fixOrphanUserMessages(history);

        List<List<Map<String, Object>>> traceUserMentions = new ArrayList<>();
        for (Message msg : history) {
            if (msg.getRole() == MessageRole.USER) {
                List<ChatAttachmentDTO> histAttachments = parseAttachmentsFromMetadata(msg.getMetadata());
                messages.add(buildUserMessageForAttachments(msg.getContent(), histAttachments, sessionId, agentConfigMap));
                traceUserMentions.add(parseMentionsFromMetadata(msg.getMetadata()));
            } else if (msg.getRole() == MessageRole.ASSISTANT) {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }

        // 6. 当前用户消息（文档走文本注入，图片/视频走多模态）
        // 6.1 引用回复：将被引用消息内容注入到用户消息前，让 LLM 理解追问上下文
        Long replyToId = request != null ? request.getReplyToMessageId() : null;
        String effectiveUserMessage = userMessage;
        if (replyToId != null) {
            String replyToContent = resolveReplyToContent(replyToId);
            if (replyToContent != null && !replyToContent.isBlank()) {
                effectiveUserMessage = "[引用消息：" + replyToContent + "]\n" + userMessage;
            }
        }
        List<ChatAttachmentDTO> attachments = request != null ? request.getAttachments() : null;
        messages.add(buildUserMessageForAttachments(
                appendMentionHintIfNeeded(effectiveUserMessage, ctx), attachments, sessionId, agentConfigMap));
        List<ChatMentionDTO> currentMentions = null;
        if (ctx != null && ctx.getMentionScope() != null && ctx.getMentionScope().getRawMentions() != null) {
            currentMentions = ctx.getMentionScope().getRawMentions();
        } else if (request != null) {
            currentMentions = request.getMentions();
        }
        Long agentVersionId = request != null ? request.getAgentVersionId() : null;
        traceUserMentions.add(buildMentionSnapshots(
                currentMentions != null ? currentMentions : List.of(), agentVersionId));
        if (ctx != null) {
            ctx.setTraceUserMentionsPerMessage(traceUserMentions);
        }
        return messages;
    }

    /**
     * 追加当前会话的 todos 快照到 system prompt，避免 AI 在长调研中漏传导致丢项。
     * <p>仅当存在未完结项时追加；同时把快照写入 ctx.currentTodosSnapshot，
     * 供后续 WriteTodosTool 按 id 合并时读取（本轮多次 write_todos 调用共享同一份累加快照）。
     * 读取失败静默返回原 prompt 不影响主链路。</p>
     */
    private String appendCurrentTodosPrompt(String systemPrompt, ChatContext ctx) {
        if (ctx == null || ctx.getSessionId() == null
                || ctx.getRequestId() == null || ctx.getRequestId().isBlank()) {
            return systemPrompt;
        }
        try {
            List<TodoItemVO> todos = sessionTodoService.listByRequest(ctx.getSessionId(), ctx.getRequestId());
            if (todos == null || todos.isEmpty()) {
                return systemPrompt;
            }
            // 初始化本轮内存快照：WriteTodosTool 按 id 合并基准，每次 write_todos 成功后由 executeToolCallback 回写
            if (ctx.getCurrentTodosSnapshot() == null) {
                List<Map<String, String>> snapshot = new ArrayList<>(todos.size());
                for (TodoItemVO t : todos) {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("id", t.getId() != null ? t.getId() : "");
                    m.put("content", t.getContent() != null ? t.getContent() : "");
                    m.put("status", t.getStatus() != null ? t.getStatus() : "pending");
                    snapshot.add(m);
                }
                ctx.setCurrentTodosSnapshot(snapshot);
            }
            StringBuilder sb = new StringBuilder("\n\n# 当前会话待办快照\n\n");
            sb.append("调用 write_todos 时遵循以下合并语义：\n");
            sb.append("- 已完成（completed）/已取消（cancelled）项不要重传，系统会自动保留\n");
            sb.append("- 未提及的 pending/in_progress 项会保留，不需要重传\n");
            sb.append("- **只传需要更新 status 的项或新增项**，不要重写整张清单\n");
            sb.append("- 不要重复新增已存在的 id\n\n");
            sb.append("| id | content | status |\n");
            sb.append("|----|---------|--------|\n");
            for (TodoItemVO t : todos) {
                sb.append("| ").append(safe(t.getId()))
                        .append(" | ").append(safe(t.getContent()))
                        .append(" | ").append(safe(t.getStatus())).append(" |\n");
            }
            // 强制约束：本轮必须把所有 pending/in_progress 推进到 completed/cancelled 才能结束
            // 防止 AI 写了 todos 却中途收尾，剩余项悬挂
            sb.append("\n## 强制结束约束\n");
            sb.append("本轮回复结束前，必须确保上面所有 todos 都已变为 completed 或 cancelled。\n");
            sb.append("- 只要还有 pending 或 in_progress 的项，必须继续调用工具推进，**不得提前输出总结/结束语**\n");
            sb.append("- 完成一项后立即调用 write_todos 更新该项 status=completed，再继续下一项\n");
            sb.append("- 全部完成后才能输出最终总结并结束本轮回复\n");
            return systemPrompt + sb.toString();
        } catch (Exception e) {
            return systemPrompt;
        }
    }

    /** 表格单元格转义：替换换行/竖线，避免破坏 Markdown 表格结构 */
    private String safe(String value) {
        if (value == null) return "";
        return value.replace("|", "\\|").replace("\n", " ").trim();
    }

    private String appendUserMemoryPrompt(String systemPrompt, ChatContext ctx, Agent agent, String userMessage) {
        if (ctx == null || ctx.getRequest() == null) {
            return systemPrompt;
        }
        // 自动化调度：不注入长期记忆
        if (ctx.getRequest().getActorUserId() != null && ctx.getRequest().getApiKeyId() == null) {
            return systemPrompt;
        }
        try {
            Long apiKeyId = ctx.getRequest().getApiKeyId();
            LongMemoryPolicyVO policy = longMemoryPolicyService.resolveEffective(apiKeyId);
            if (!Boolean.TRUE.equals(policy.getEnabled())) {
                return systemPrompt;
            }
            com.lightbot.dto.MemoryScope scope = resolveMemoryScope(ctx);
            if (scope == null || !scope.isValid()) {
                return systemPrompt;
            }
            Long memoryAgentId = null;
            if ("agent".equalsIgnoreCase(policy.getScope()) && agent != null) {
                memoryAgentId = agent.getId();
            }
            int injectLimit = policy.getInjectLimit() != null ? policy.getInjectLimit() : 6;
            String memoryPrompt = userMemoryService.buildMemoryPrompt(scope, memoryAgentId, userMessage, injectLimit);
            return memoryPrompt.isBlank() ? systemPrompt : systemPrompt + memoryPrompt;
        } catch (Exception e) {
            log.warn("[MessageMiddleware] 加载长期记忆失败: apiKeyId={}, userId={}, error={}",
                    ctx.getRequest().getApiKeyId(), ctx.getUserId(), e.getMessage());
            return systemPrompt;
        }
    }

    /**
     * 开放 API：Key + externalUserId；控制台：debug_user_{登录用户ID}
     */
    private com.lightbot.dto.MemoryScope resolveMemoryScope(ChatContext ctx) {
        if (ctx.getRequest().getApiKeyId() != null) {
            String externalUserId = ctx.getRequest().getExternalUserId();
            if (externalUserId == null || externalUserId.isBlank()) {
                return null;
            }
            return com.lightbot.dto.MemoryScope.external(ctx.getRequest().getApiKeyId(), externalUserId);
        }
        if (ctx.getUserId() == null) {
            return null;
        }
        return com.lightbot.dto.MemoryScope.consoleDebug(ctx.getUserId());
    }

    /**
     * 版本快照优先，避免草稿/已发布版本的绑定范围混用；未使用版本时回退到 Agent 当前绑定。
     */
    private boolean hasBoundSubAgents(ChatContext ctx, Agent agent) {
        if (ctx != null && ctx.getVersionSubAgentIds() != null) {
            return !ctx.getVersionSubAgentIds().isEmpty();
        }
        if (agent == null || agent.getId() == null) {
            return false;
        }
        try {
            return !agentService.getSubAgentIds(agent.getId()).isEmpty();
        } catch (Exception e) {
            log.warn("[Chat] 读取 Agent 子智能体绑定失败: agentId={}, error={}", agent.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * 用户 @ 了资源时，在用户消息前追加优先使用提示，帮助模型理解意图
     */
    private String appendMentionHintIfNeeded(String userMessage, ChatContext ctx) {
        if (ctx == null || ctx.getMentionScope() == null) {
            return userMessage;
        }
        return MentionHintBuilder.prependUserMessageHint(userMessage, ctx.getMentionScope().getRawMentions());
    }

    /**
     * 构建用户消息：document 附件拼入文本；图片按能力分流——多模态 Agent 走 image_url，
     * 非多模态 Agent 图片仅提示可用 ocr_parse_file 识别；视频始终走多模态。
     */
    private org.springframework.ai.chat.messages.Message buildUserMessageForAttachments(
            String content, List<ChatAttachmentDTO> attachments, Long sessionId,
            Map<String, Object> agentConfigMap) {
        if (attachments == null || attachments.isEmpty()) {
            return new UserMessage(content != null ? content : "");
        }
        List<ChatAttachmentDTO> documents = ChatDocumentMessageUtil.filterDocuments(attachments);
        String text = chatAttachmentParsedService.wrapUserMessage(content, documents, sessionId);

        AgentChatCapabilitiesDTO caps = AgentChatCapabilitiesUtil.fromConfigMap(agentConfigMap);
        boolean imageAsMedia = Boolean.TRUE.equals(caps.getEnableImageInput());

        List<ChatAttachmentDTO> images = ChatDocumentMessageUtil.filterImages(attachments);
        List<ChatAttachmentDTO> videos = ChatDocumentMessageUtil.filterVideos(attachments);

        // 多模态图片 + 视频走 image_url；非多模态图片改注入 OCR 提示，不喂模型
        List<ChatAttachmentDTO> media = new ArrayList<>(videos);
        if (imageAsMedia) {
            media.addAll(images);
        } else if (!images.isEmpty()) {
            text = chatAttachmentParsedService.wrapImagesForOcr(text, images);
        }

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
     * <p>压缩开始/完成/失败时通过 ctx.preStreamStatusEvents 推送 SSE 事件，
     * 让前端展示占位提示；事件由 execute() 取出并 prepend 到主 Flux</p>
     */
    private List<Message> summarizeIfNeeded(List<Message> history, Map<String, Object> configMap, Agent agent, ChatContext ctx) {
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

        // 推送 started 事件：附原始条数与体积，让前端展示"正在压缩 N 条 / M KB"
        emitPreStreamEvent(ctx, toolEventGenerator.contextCompressionEvent("started",
                "压缩 " + history.size() + " 条 / " + String.format("%.1f", totalKb) + "KB"));

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
                emitPreStreamEvent(ctx, toolEventGenerator.contextCompressionEvent("failed", "摘要内容为空，使用原始上下文"));
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
            // 推送 completed 事件：附压缩后条数，让前端停止占位动画
            emitPreStreamEvent(ctx, toolEventGenerator.contextCompressionEvent("completed",
                    "压缩完成，保留 " + result.size() + " 条"));
            return result;
        } catch (Exception e) {
            log.warn("[Chat][Summary] 摘要生成失败，使用原始上下文: {}", e.getMessage());
            emitPreStreamEvent(ctx, toolEventGenerator.contextCompressionEvent("failed",
                    "摘要生成失败：" + e.getMessage()));
            return history;
        }
    }

    /**
     * 把 SSE 状态事件追加到 ctx.preStreamStatusEvents，
     * 由 execute() 在 next.proceed 之前 prepend 到主 Flux 下发
     */
    private void emitPreStreamEvent(ChatContext ctx, String statusJson) {
        if (ctx != null && statusJson != null && !statusJson.isBlank()) {
            ctx.getPreStreamStatusEvents().add(ToolEventGenerator.STATUS_PREFIX + statusJson);
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
    public Long saveMessage(Long sessionId, MessageRole role, String content, String metadata,
                            int tokenCount, MessageType messageType, Long parentId) {
        return saveMessage(sessionId, role, content, metadata, tokenCount, messageType, parentId, null);
    }

    /**
     * 持久化消息（含 messageType、parentId 和 replyToMessageId）
     *
     * @return 消息ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long saveMessage(Long sessionId, MessageRole role, String content, String metadata,
                            int tokenCount, MessageType messageType, Long parentId, Long replyToMessageId) {
        return saveMessage(sessionId, role, content, metadata, null,
                tokenCount, messageType, parentId, replyToMessageId);
    }

    /**
     * 持久化消息（含 toolEvents 工具事件流，与 metadata 解耦）
     *
     * @param toolEvents 工具事件流 JSON 字符串；可为 null（无工具调用）
     * @return 消息ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long saveMessage(Long sessionId, MessageRole role, String content, String metadata, String toolEvents,
                            int tokenCount, MessageType messageType, Long parentId, Long replyToMessageId) {
        Message msg = new Message();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(com.lightbot.util.TextNormalizeUtil.sanitizeForDatabase(content));
        msg.setContentType(ContentType.TEXT);
        msg.setMessageType(messageType != null ? messageType : MessageType.TEXT);
        msg.setTokenCount(tokenCount);
        msg.setMetadata(com.lightbot.util.TextNormalizeUtil.sanitizeForDatabase(metadata));
        msg.setToolEvents(com.lightbot.util.TextNormalizeUtil.sanitizeForDatabase(toolEvents));
        msg.setParentId(parentId);
        msg.setReplyToMessageId(replyToMessageId);
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
     * 重新生成：按 ID 删除助手消息（及统计），须属于当前会话且为最后一轮用户消息之后的回复
     */
    public void deleteAssistantMessageById(Long sessionId, Long messageId) {
        if (sessionId == null || messageId == null) {
            return;
        }
        Message target = messageMapper.selectById(messageId);
        if (target == null) {
            log.info("[MessageMiddleware] regenerate 跳过删除：messageId={} 在库中不存在", messageId);
            return;
        }
        if (!sessionId.equals(target.getSessionId())
                || target.getRole() != MessageRole.ASSISTANT) {
            log.info("[MessageMiddleware] regenerate 跳过删除：messageId={} 不属于当前会话或非助手消息", messageId);
            return;
        }
        Message lastUser = messageMapper.selectOne(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getSessionId, sessionId)
                        .eq(Message::getRole, MessageRole.USER)
                        .orderByDesc(Message::getCreateTime)
                        .last("LIMIT 1"));
        if (lastUser != null && lastUser.getCreateTime() != null && target.getCreateTime() != null
                && target.getCreateTime().isBefore(lastUser.getCreateTime())) {
            log.warn("[MessageMiddleware] 跳过删除助手消息 id={}：早于最后一条用户消息，可能为误删历史回复", messageId);
            return;
        }
        messageMapper.deleteById(messageId);
        var session = chatSessionService.getById(sessionId);
        if (session != null && session.getMessageCount() != null && session.getMessageCount() > 0) {
            session.setMessageCount(session.getMessageCount() - 1);
            int tokens = target.getTokenCount() != null ? target.getTokenCount() : 0;
            if (tokens > 0 && session.getTotalTokens() != null) {
                session.setTotalTokens(Math.max(0L, session.getTotalTokens() - tokens));
            }
            chatSessionService.updateById(session);
        }
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
     * 读取当前 Agent 的最大执行步数用于提示词插值，与 ChatServiceImpl.resolveMaxExecutionSteps 保持一致的默认值 20。
     * 仅用于提示语句渲染，允许配置缺失时返回默认值，不做上限裁剪（提示文案已明确"约 N 步"）。
     */
    private int resolveMaxExecutionStepsHint(Map<String, Object> configMap) {
        if (configMap == null) {
            return 20;
        }
        Object val = configMap.get(ConfigKeys.Agent.MAX_EXECUTION_STEPS);
        if (val instanceof Number n) {
            return Math.max(1, n.intValue());
        }
        if (val != null) {
            try {
                return Math.max(1, Integer.parseInt(val.toString()));
            } catch (Exception ignored) {
            }
        }
        return 20;
    }

    /**
     * 修复孤立 USER 消息：在没有 ASSISTANT 配对的 USER 消息后插入占位 ASSISTANT 消息，
     * 避免连续 USER 消息导致 LLM 误判为需要回答多条。
     * <p>不修改 DB，仅修改传入的 history 列表（用于 LLM 上下文构建）。</p>
     */
    private void fixOrphanUserMessages(List<Message> history) {
        if (history.isEmpty()) {
            return;
        }
        // 1. 从后向前扫描，在连续 USER 之间插入占位 ASSISTANT
        for (int i = history.size() - 1; i >= 1; i--) {
            Message cur = history.get(i);
            Message prev = history.get(i - 1);
            if (cur.getRole() == MessageRole.USER && prev.getRole() == MessageRole.USER) {
                Message placeholder = new Message();
                placeholder.setRole(MessageRole.ASSISTANT);
                placeholder.setContent("（未完成的回复）");
                placeholder.setSessionId(cur.getSessionId());
                placeholder.setCreateTime(cur.getCreateTime().minusSeconds(1));
                history.add(i, placeholder);
            }
        }
        // 2. 末尾孤立 USER：最后一条是 USER（无论前面是什么），当前消息也是 USER，
        //    需要在末尾插入占位 ASSISTANT，防止 LLM 看到连续两条 USER
        if (history.get(history.size() - 1).getRole() == MessageRole.USER) {
            Message last = history.get(history.size() - 1);
            Message placeholder = new Message();
            placeholder.setRole(MessageRole.ASSISTANT);
            placeholder.setContent("（未完成的回复）");
            placeholder.setSessionId(last.getSessionId());
            placeholder.setCreateTime(last.getCreateTime().plusSeconds(1));
            history.add(placeholder);
        }
    }

    /**
     * 从消息 metadata 解析 mention 快照（供 Trace 历史消息回显）
     */
    private List<Map<String, Object>> parseMentionsFromMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return List.of();
        }
        try {
            Map<String, Object> meta = objectMapper.readValue(metadata, new TypeReference<>() {});
            Object mentions = meta.get("mentions");
            if (!(mentions instanceof List<?> list)) {
                return List.of();
            }
            List<Map<String, Object>> snapshots = new ArrayList<>(list.size());
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> snap = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : raw.entrySet()) {
                    if (entry.getKey() != null) {
                        snap.put(entry.getKey().toString(), entry.getValue());
                    }
                }
                snapshots.add(snap);
            }
            return snapshots;
        } catch (Exception e) {
            log.debug("[Chat] 解析消息 mention metadata 失败: {}", e.getMessage());
            return List.of();
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

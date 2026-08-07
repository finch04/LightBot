package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.common.Result;
import cn.dev33.satoken.stp.StpUtil;
import com.lightbot.dto.ChatRequestDTO;
import com.lightbot.dto.MessageFeedbackRequestDTO;
import com.lightbot.vo.MessageFeedbackVO;
import com.lightbot.vo.RagReferenceVO;
import com.lightbot.dto.ReconnectDTO;
import com.lightbot.entity.MessageFeedback;
import com.lightbot.interceptor.ApiKeyAuthInterceptor;
import com.lightbot.service.ChatService;
import com.lightbot.service.MessageFeedbackService;
import com.lightbot.service.chat.ChatStreamSseHelper;
import com.lightbot.service.chat.SseEventBuffer;
import jakarta.validation.Valid;
import com.lightbot.constant.EnterpriseActors;
import com.lightbot.dto.ChatAttachmentDTO;
import com.lightbot.service.ChatAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "AI对话", description = "基于通义千问的AI对话接口")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatAttachmentService chatAttachmentService;
    private final MessageFeedbackService messageFeedbackService;
    private final SseEventBuffer eventBuffer;
    private final ChatStreamSseHelper chatStreamSseHelper;

    @Operation(summary = "同步对话")
    @PostMapping
    public Result<String> chat(@Valid @RequestBody ChatRequestDTO request,
                               jakarta.servlet.http.HttpServletRequest httpRequest) {
        injectApiKey(request, httpRequest);
        return Result.ok(chatService.chat(request));
    }

    /**
     * 流式对话（SSE）
     * <p>SSE 订阅编排（emitter 创建 / 背压 / 心跳 / 事件 ID / 缓冲 / disposal）下沉到
     * {@link ChatStreamSseHelper}，Controller 仅负责注入 API Key 与获取当前 userId。</p>
     */
    @Operation(summary = "流式对话（SSE）")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequestDTO request,
                                 jakarta.servlet.http.HttpServletRequest httpRequest) {
        injectApiKey(request, httpRequest);
        Long userId = resolveChatActorId(httpRequest, request.getApiKeyId());
        Flux<String> flux = chatService.chatStream(request);
        return chatStreamSseHelper.subscribe(flux, userId);
    }

    private void injectApiKey(ChatRequestDTO request, jakarta.servlet.http.HttpServletRequest httpRequest) {
        Object apiKeyAttr = httpRequest.getAttribute(ApiKeyAuthInterceptor.ATTR_API_KEY_ENTITY);
        if (apiKeyAttr instanceof com.lightbot.entity.ApiKey apiKey) {
            request.setApiKeyId(apiKey.getId());
        }
    }

    /**
     * 对话归属身份：企业 API Key → 虚拟身份；否则登录用户
     */
    private Long resolveChatActorId(jakarta.servlet.http.HttpServletRequest httpRequest, Long apiKeyId) {
        if (apiKeyId != null || httpRequest.getAttribute(ApiKeyAuthInterceptor.ATTR_API_KEY_USER_ID) != null) {
            return EnterpriseActors.API_KEY;
        }
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * SSE 断线重连：获取缓冲事件或完成状态
     * <p>前端断线后携带 requestId + lastEventId 调用此端点，
     * 服务端返回缓冲的事件供前端重放，或告知已完成/未找到。</p>
     */
    @Operation(summary = "SSE断线重连")
    @PostMapping("/reconnect")
    public Result<Map<String, Object>> reconnect(@Valid @RequestBody ReconnectDTO req,
                                                 jakarta.servlet.http.HttpServletRequest httpRequest) {
        Long userId = resolveChatActorId(httpRequest, null);
        if (userId == null) {
            return Result.fail(401, "未登录");
        }

        SseEventBuffer.ReconnectResult result = eventBuffer.getReconnectData(
                req.getRequestId(), req.getLastEventId(), userId);

        return switch (result.status()) {
            case NOT_FOUND -> Result.fail(404, "请求不存在或已过期");
            case ALREADY_DELIVERED -> Result.ok(Map.of("status", "already_delivered"));
            case COMPLETED, CANCELLED -> {
                List<Map<String, Object>> events = result.events().stream()
                        .map(e -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("id", e.id());
                            m.put("data", e.data());
                            return m;
                        })
                        .toList();
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("status", result.status().name().toLowerCase());
                body.put("events", events);
                yield Result.ok(body);
            }
        };
    }

    @Operation(summary = "停止流式对话")
    @PostMapping("/stream/stop")
    public Result<Void> stopStream(@RequestParam String requestId,
                                   jakarta.servlet.http.HttpServletRequest httpRequest) {
        Long userId = resolveChatActorId(httpRequest, null);
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        chatService.stopStream(requestId, userId);
        return Result.ok();
    }

    @Operation(summary = "上传对话附件（图片/视频/文档）")
    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ChatAttachmentDTO> uploadAttachment(
            @RequestParam Long agentId,
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) Integer configVersion,
            @RequestParam("file") MultipartFile file) {
        return Result.ok(chatAttachmentService.upload(agentId, sessionId, configVersion, file));
    }

    @Operation(summary = "删除未发送的缓冲区附件")
    @DeleteMapping("/attachments")
    public Result<Void> deleteAttachment(
            @RequestParam Long agentId,
            @RequestParam(required = false) Long sessionId,
            @Valid @RequestBody ChatAttachmentDTO attachment) {
        chatAttachmentService.delete(agentId, sessionId, attachment);
        return Result.ok();
    }

    @Operation(summary = "刷新对话附件预览 URL")
    @PostMapping("/attachments/refresh-preview")
    public Result<List<ChatAttachmentDTO>> refreshAttachmentPreview(@Valid @RequestBody List<ChatAttachmentDTO> attachments) {
        return Result.ok(chatAttachmentService.refreshPreviewUrls(attachments));
    }

    @Operation(summary = "获取RAG引用信息")
    @GetMapping("/rag-references")
    public Result<List<RagReferenceVO>> getRagReferences(
            @RequestParam Long sessionId,
            @RequestParam(required = false) Long agentId,
            @RequestParam String question) {
        return Result.ok(chatService.getRagReferences(sessionId, agentId, question));
    }

    // ========== 消息反馈 ==========

    @Operation(summary = "提交消息反馈（👍/👎，重复提交切换/取消）")
    @PostMapping("/messages/{messageId}/feedback")
    public Result<MessageFeedback> submitMessageFeedback(
            @PathVariable Long messageId,
            @Valid @RequestBody MessageFeedbackRequestDTO request) {
        long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(messageFeedbackService.submitFeedback(messageId, userId, request));
    }

    @Operation(summary = "获取当前用户对指定消息的反馈")
    @GetMapping("/messages/{messageId}/feedback")
    public Result<MessageFeedback> getMessageFeedback(@PathVariable Long messageId) {
        long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(messageFeedbackService.getMyFeedback(messageId, userId));
    }

    @Operation(summary = "获取当前用户的所有反馈记录（分页）")
    @GetMapping("/feedbacks")
    public Result<Page<MessageFeedbackVO>> listMyFeedbacks(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String rating) {
        long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(messageFeedbackService.listMyFeedbacks(userId, pageNum, pageSize, rating));
    }

    @Operation(summary = "获取当前用户的反馈统计")
    @GetMapping("/feedbacks/stats")
    public Result<Map<String, Object>> getFeedbackStats() {
        long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(messageFeedbackService.getFeedbackStats(userId));
    }

    @Operation(summary = "批量获取消息反馈状态")
    @PostMapping("/messages/feedbacks/batch")
    public Result<Map<Long, MessageFeedback>> batchGetFeedbacks(@RequestBody List<Long> messageIds) {
        long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(messageFeedbackService.batchGetFeedbacks(userId, messageIds));
    }
}

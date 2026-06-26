package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.common.Result;
import com.lightbot.dto.ChatRequest;
import com.lightbot.dto.MessageFeedbackRequest;
import com.lightbot.dto.MessageFeedbackVO;
import com.lightbot.dto.RagReferenceVO;
import com.lightbot.entity.MessageFeedback;
import com.lightbot.service.ChatService;
import com.lightbot.service.MessageFeedbackService;
import jakarta.validation.Valid;
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
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Tag(name = "AI对话", description = "基于通义千问的AI对话接口")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatAttachmentService chatAttachmentService;
    private final MessageFeedbackService messageFeedbackService;

    /** SSE 超时时间：5分钟（长文本生成可能较慢） */
    private static final long SSE_TIMEOUT = 5 * 60 * 1000L;

    @Operation(summary = "同步对话")
    @PostMapping
    public Result<String> chat(@Valid @RequestBody ChatRequest request) {
        return Result.ok(chatService.chat(request));
    }

    /** SSE 心跳前缀（协议注释行） */
    private static final String HEARTBEAT_PREFIX = ":heartbeat";

    /**
     * 流式对话（SSE）
     * <p>使用 SseEmitter 替代 Flux<String>，避免 Spring MVC Reactor Servlet 桥接层的缓冲问题，
     * 确保每个 token 到达时立即刷新到客户端。</p>
     * <p>支持：心跳保活（SSE 注释行）、事件 ID（用于断线重连）、结构化错误事件。</p>
     */
    @Operation(summary = "流式对话（SSE）")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        AtomicInteger eventIdCounter = new AtomicInteger(0);

        // 在 boundedElastic 调度器上订阅 Flux，避免阻塞 Servlet 线程
        Flux<String> flux = chatService.chatStream(request);
        flux.publishOn(Schedulers.boundedElastic())
                .subscribe(
                        chunk -> {
                            try {
                                // 心跳注释行：SSE 协议规定以冒号开头的行为注释，客户端应忽略
                                if (HEARTBEAT_PREFIX.equals(chunk)) {
                                    emitter.send(SseEmitter.event().comment("heartbeat"));
                                    return;
                                }
                                // 文本内容中的换行符需要转义，否则SSE解析会丢失
                                // STATUS/METADATA/DONE 前缀的事件不含换行，无需处理
                                String safe = chunk.startsWith("[STATUS]") || chunk.startsWith("[METADATA]")
                                        || chunk.startsWith("[DONE]") || chunk.startsWith("[REQUEST_ID]")
                                        ? chunk
                                        : chunk.replace("\n", "\\n");
                                // 1.2 每个数据事件携带递增 ID，支持前端断线重连时通过 Last-Event-ID 恢复
                                String eventId = String.valueOf(eventIdCounter.incrementAndGet());
                                emitter.send(SseEmitter.event().id(eventId).data(safe));
                            } catch (IOException e) {
                                log.debug("[Chat] 客户端断开连接: {}", e.getMessage());
                            }
                        },
                        emitter::completeWithError,
                        emitter::complete
                );

        // 清理回调
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> log.warn("[Chat] SSE连接异常: {}", e.getMessage()));

        return emitter;
    }

    @Operation(summary = "上传对话附件（图片/视频）")
    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ChatAttachmentDTO> uploadAttachment(
            @RequestParam Long agentId,
            @RequestParam(required = false) Long sessionId,
            @RequestParam("file") MultipartFile file) {
        return Result.ok(chatAttachmentService.upload(agentId, sessionId, file));
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
            @Valid @RequestBody MessageFeedbackRequest request) {
        return Result.ok(messageFeedbackService.submitFeedback(messageId, request));
    }

    @Operation(summary = "获取当前用户对指定消息的反馈")
    @GetMapping("/messages/{messageId}/feedback")
    public Result<MessageFeedback> getMessageFeedback(@PathVariable Long messageId) {
        return Result.ok(messageFeedbackService.getMyFeedback(messageId));
    }

    @Operation(summary = "获取当前用户的所有反馈记录（分页）")
    @GetMapping("/feedbacks")
    public Result<Page<MessageFeedbackVO>> listMyFeedbacks(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(messageFeedbackService.listMyFeedbacks(pageNum, pageSize));
    }

    @Operation(summary = "获取当前用户的反馈统计")
    @GetMapping("/feedbacks/stats")
    public Result<Map<String, Object>> getFeedbackStats() {
        return Result.ok(messageFeedbackService.getFeedbackStats());
    }

    @Operation(summary = "批量获取消息反馈状态")
    @PostMapping("/messages/feedbacks/batch")
    public Result<Map<Long, MessageFeedback>> batchGetFeedbacks(@RequestBody List<Long> messageIds) {
        return Result.ok(messageFeedbackService.batchGetFeedbacks(messageIds));
    }
}

package com.lightbot.controller;

import com.lightbot.common.Result;
import com.lightbot.dto.ChatRequest;
import com.lightbot.dto.RagReferenceVO;
import com.lightbot.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.List;

@Slf4j
@Tag(name = "AI对话", description = "基于通义千问的AI对话接口")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** SSE 超时时间：5分钟（长文本生成可能较慢） */
    private static final long SSE_TIMEOUT = 5 * 60 * 1000L;

    @Operation(summary = "同步对话")
    @PostMapping
    public Result<String> chat(@Valid @RequestBody ChatRequest request) {
        return Result.ok(chatService.chat(request));
    }

    /**
     * 流式对话（SSE）
     * <p>使用 SseEmitter 替代 Flux<String>，避免 Spring MVC Reactor Servlet 桥接层的缓冲问题，
     * 确保每个 token 到达时立即刷新到客户端。</p>
     */
    @Operation(summary = "流式对话（SSE）")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        // 在 boundedElastic 调度器上订阅 Flux，避免阻塞 Servlet 线程
        Flux<String> flux = chatService.chatStream(request);
        flux.publishOn(Schedulers.boundedElastic())
                .subscribe(
                        chunk -> {
                            try {
                                emitter.send(chunk);
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

    @Operation(summary = "获取RAG引用信息")
    @GetMapping("/rag-references")
    public Result<List<RagReferenceVO>> getRagReferences(
            @RequestParam Long sessionId,
            @RequestParam(required = false) Long agentId,
            @RequestParam String question) {
        return Result.ok(chatService.getRagReferences(sessionId, agentId, question));
    }
}

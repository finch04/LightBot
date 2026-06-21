package com.lightbot.controller;

import com.lightbot.common.Result;
import com.lightbot.dto.RagSearchResultVO;
import com.lightbot.service.RagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 知识库RAG问答接口
 *
 * @author finch
 * @since 2026-06-21
 */
@Tag(name = "知识库RAG问答", description = "基于知识库的RAG问答、检索测试")
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeRagController {

    private final RagService ragService;

    @Operation(summary = "基于知识库RAG问答（同步）")
    @PostMapping("/{id}/ask")
    public Result<String> ask(@PathVariable Long id,
                              @RequestParam String question,
                              @RequestParam(required = false) Long providerId) {
        return Result.ok(ragService.ask(id, question, providerId));
    }

    @Operation(summary = "基于知识库RAG问答（流式）")
    @PostMapping(value = "/{id}/ask-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@PathVariable Long id,
                                @RequestParam String question,
                                @RequestParam(required = false) Long providerId) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);

        Flux<String> flux = ragService.askStream(id, question, providerId);
        flux.publishOn(Schedulers.boundedElastic())
                .subscribe(
                        chunk -> {
                            try {
                                emitter.send(chunk);
                            } catch (IOException e) {
                                // 客户端断开，忽略
                            }
                        },
                        emitter::completeWithError,
                        emitter::complete
                );

        emitter.onTimeout(emitter::complete);
        return emitter;
    }

    @Operation(summary = "检索测试（纯向量检索，不调用LLM，支持overrides覆盖参数）")
    @PostMapping("/{id}/search")
    public Result<List<RagSearchResultVO>> search(@PathVariable Long id,
                                                   @RequestBody Map<String, Object> body) {
        String question = (String) body.get("question");
        @SuppressWarnings("unchecked")
        Map<String, Object> overrides = (Map<String, Object>) body.get("overrides");
        return Result.ok(ragService.search(id, question, overrides));
    }
}

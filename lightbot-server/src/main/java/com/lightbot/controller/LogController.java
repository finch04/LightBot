package com.lightbot.controller;

import com.lightbot.common.Result;
import com.lightbot.log.LogEvent;
import com.lightbot.log.MemoryLogAppender;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 日志监控接口，提供历史查询 + 实时 SSE 推送
 *
 * @author finch
 * @since 2026-05-20
 */
@Tag(name = "日志监控", description = "实时日志查看")
@RestController
@RequestMapping("/api/logs")
public class LogController {

    /** SSE 超时时间：30分钟 */
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;

    /** 活跃的 SSE 连接 */
    private static final CopyOnWriteArrayList<SseEmitter> EMITTERS = new CopyOnWriteArrayList<>();

    static {
        // 注册日志事件监听，推送到所有 SSE 连接
        MemoryLogAppender.subscribe(event -> {
            for (SseEmitter emitter : EMITTERS) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("log")
                            .data(event, MediaType.APPLICATION_JSON));
                } catch (IOException | IllegalStateException e) {
                    EMITTERS.remove(emitter);
                }
            }
        });
    }

    @Operation(summary = "获取最近日志（历史）")
    @GetMapping("/recent")
    public Result<List<LogEvent>> recentLogs(
            @RequestParam(defaultValue = "200") int limit) {
        return Result.ok(MemoryLogAppender.getRecentLogs(Math.min(limit, 2000)));
    }

    @Operation(summary = "实时日志推送（SSE）")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        EMITTERS.add(emitter);

        emitter.onCompletion(() -> EMITTERS.remove(emitter));
        emitter.onTimeout(() -> EMITTERS.remove(emitter));
        emitter.onError(e -> EMITTERS.remove(emitter));

        return emitter;
    }
}

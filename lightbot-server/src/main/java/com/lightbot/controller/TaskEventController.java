package com.lightbot.controller;

import com.lightbot.enums.TaskStatus;
import com.lightbot.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务事件 SSE 推送，实时通知前端运行中任务数量变化
 * <p>任务创建/状态变更时由 TaskServiceImpl 主动触发推送</p>
 *
 * @author finch
 * @since 2026-05-21
 */
@Slf4j
@Tag(name = "任务事件", description = "任务状态实时推送")
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskEventController {

    private final TaskService taskService;

    /** 每个用户一个 SSE 连接 */
    private static final Map<Long, SseEmitter> USER_EMITTERS = new ConcurrentHashMap<>();

    @Operation(summary = "任务计数实时推送（SSE）")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        Long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        // 关闭同一用户的旧连接
        SseEmitter old = USER_EMITTERS.put(userId, emitter);
        if (old != null) {
            old.complete();
        }
        emitter.onCompletion(() -> USER_EMITTERS.remove(userId, emitter));
        emitter.onTimeout(() -> USER_EMITTERS.remove(userId, emitter));
        emitter.onError(e -> USER_EMITTERS.remove(userId, emitter));

        // 立即推送当前计数
        sendCount(userId, emitter);

        return emitter;
    }

    /**
     * 供 TaskServiceImpl 在任务创建/状态变更时调用，立即推送最新计数给对应用户
     */
    public void pushToUser(Long userId) {
        SseEmitter emitter = USER_EMITTERS.get(userId);
        if (emitter != null) {
            sendCount(userId, emitter);
        }
    }

    private void sendCount(Long userId, SseEmitter emitter) {
        try {
            long pending = taskService.countByStatus(userId, TaskStatus.PENDING.getCode());
            long running = taskService.countByStatus(userId, TaskStatus.RUNNING.getCode());
            long active = pending + running;
            // 推送分状态计数 JSON，前端同时用于导航角标和任务中心
            emitter.send(SseEmitter.event().name("count")
                    .data(Map.of("active", active, "pending", pending, "running", running)));
        } catch (IOException | IllegalStateException e) {
            USER_EMITTERS.remove(userId, emitter);
        } catch (Exception e) {
            log.warn("[TaskEvent] 推送失败, userId={}", userId, e);
        }
    }
}

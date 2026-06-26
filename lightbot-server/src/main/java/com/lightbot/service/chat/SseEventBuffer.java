package com.lightbot.service.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 事件缓冲：为每个请求缓存最近事件，支持断线重连时重放
 *
 * @author finch
 */
@Slf4j
@Component
public class SseEventBuffer {

    private static final int MAX_EVENTS = 100;
    private static final long TTL_MS = 5 * 60 * 1000L;

    private final ConcurrentHashMap<String, RequestContext> buffers = new ConcurrentHashMap<>();

    /**
     * 缓冲一个 SSE 事件
     *
     * @param requestId 请求ID
     * @param eventId   事件序号
     * @param data      事件数据（原始 SSE data 内容）
     * @param userId    用户ID（首次调用时设置）
     */
    public void bufferEvent(String requestId, int eventId, String data, Long userId) {
        RequestContext ctx = buffers.computeIfAbsent(requestId, k -> new RequestContext(userId));
        synchronized (ctx.events) {
            if (ctx.events.size() >= MAX_EVENTS) {
                ctx.events.remove(0);
            }
            ctx.events.add(new BufferedEvent(eventId, data));
            ctx.maxEventId = Math.max(ctx.maxEventId, eventId);
        }
    }

    /**
     * 标记请求已完成（[DONE] 已发送）
     */
    public void markCompleted(String requestId) {
        RequestContext ctx = buffers.get(requestId);
        if (ctx != null) {
            ctx.completed = true;
        }
    }

    /**
     * 获取重连数据：返回缓冲的事件供前端重放
     *
     * @param requestId   请求ID
     * @param lastEventId 前端最后收到的事件ID（null 表示全部）
     * @param userId      当前用户ID（鉴权）
     * @return 重连结果
     */
    public ReconnectResult getReconnectData(String requestId, Integer lastEventId, Long userId) {
        RequestContext ctx = buffers.get(requestId);
        if (ctx == null) {
            return ReconnectResult.notFound();
        }
        // 鉴权：不泄露其他用户的请求存在
        if (ctx.userId != null && !ctx.userId.equals(userId)) {
            return ReconnectResult.notFound();
        }

        List<BufferedEvent> filtered;
        synchronized (ctx.events) {
            if (lastEventId != null && lastEventId >= ctx.maxEventId && ctx.completed) {
                return ReconnectResult.alreadyDelivered();
            }
            filtered = ctx.events.stream()
                    .filter(e -> lastEventId == null || e.id > lastEventId)
                    .toList();
        }

        if (ctx.completed) {
            return ReconnectResult.completed(filtered);
        }
        return ReconnectResult.cancelled(filtered);
    }

    /** 定时清理过期缓冲（每分钟） */
    @Scheduled(fixedRate = 60_000, initialDelay = 60_000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        int removed = 0;
        var it = buffers.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (now - entry.getValue().createdAt > TTL_MS) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("[SseEventBuffer] 清理过期缓冲: removed={}", removed);
        }
    }

    // ── 内部数据结构 ──

    private static class RequestContext {
        final Long userId;
        final List<BufferedEvent> events = new ArrayList<>();
        final long createdAt = System.currentTimeMillis();
        volatile boolean completed = false;
        volatile int maxEventId = 0;

        RequestContext(Long userId) {
            this.userId = userId;
        }
    }

    /** 缓冲的单个 SSE 事件 */
    public record BufferedEvent(int id, String data) {}

    /** 重连查询结果 */
    public record ReconnectResult(Status status, List<BufferedEvent> events) {
        public enum Status { NOT_FOUND, ALREADY_DELIVERED, COMPLETED, CANCELLED }

        static ReconnectResult notFound() { return new ReconnectResult(Status.NOT_FOUND, List.of()); }
        static ReconnectResult alreadyDelivered() { return new ReconnectResult(Status.ALREADY_DELIVERED, List.of()); }
        static ReconnectResult completed(List<BufferedEvent> events) { return new ReconnectResult(Status.COMPLETED, events); }
        static ReconnectResult cancelled(List<BufferedEvent> events) { return new ReconnectResult(Status.CANCELLED, events); }
    }
}

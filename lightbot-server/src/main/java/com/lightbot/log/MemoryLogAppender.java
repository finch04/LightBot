package com.lightbot.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.UnsynchronizedAppenderBase;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 内存日志收集器，将日志事件缓存到环形队列，并通知 SSE 订阅者
 *
 * @author finch
 * @since 2026-05-20
 */
public class MemoryLogAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    /** 最大缓存日志条数 */
    private static final int MAX_CAPACITY = 2000;

    /** 环形日志队列 */
    private static final Deque<LogEvent> LOG_BUFFER = new ConcurrentLinkedDeque<>();

    /** SSE 订阅者列表 */
    private static final CopyOnWriteArrayList<java.util.function.Consumer<LogEvent>> SUBSCRIBERS =
            new CopyOnWriteArrayList<>();

    @Override
    protected void append(ILoggingEvent event) {
        String stackTrace = null;
        IThrowableProxy proxy = event.getThrowableProxy();
        if (proxy != null) {
            stackTrace = ThrowableProxyUtil.asString(proxy);
        }

        LogEvent logEvent = new LogEvent(
                event.getTimeStamp(),
                event.getLevel().toString(),
                event.getLoggerName(),
                event.getFormattedMessage(),
                stackTrace
        );

        // 超过上限时移除最旧的
        while (LOG_BUFFER.size() >= MAX_CAPACITY) {
            LOG_BUFFER.pollFirst();
        }
        LOG_BUFFER.addLast(logEvent);

        // 通知所有 SSE 订阅者
        for (java.util.function.Consumer<LogEvent> subscriber : SUBSCRIBERS) {
            try {
                subscriber.accept(logEvent);
            } catch (Exception e) {
                // 订阅者异常不影响日志收集
            }
        }
    }

    /** 获取最近的日志列表 */
    public static java.util.List<LogEvent> getRecentLogs(int limit) {
        int size = LOG_BUFFER.size();
        int fromIndex = Math.max(0, size - limit);
        return LOG_BUFFER.stream().skip(fromIndex).toList();
    }

    /** 注册 SSE 订阅者 */
    public static void subscribe(java.util.function.Consumer<LogEvent> consumer) {
        SUBSCRIBERS.add(consumer);
    }

    /** 取消订阅 */
    public static void unsubscribe(java.util.function.Consumer<LogEvent> consumer) {
        SUBSCRIBERS.remove(consumer);
    }
}

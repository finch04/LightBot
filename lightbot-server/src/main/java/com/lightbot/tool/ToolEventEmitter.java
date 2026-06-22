package com.lightbot.tool;

import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具事件发射器
 * <p>支持两种模式：</p>
 * <ul>
 *   <li>实时推送（流式）：通过 {@link #setupSink}/{@link #teardownSink} 绑定 Sinks.Many，
 *       {@link #emit(String)} 直接推送给前端，实现工具执行过程的实时状态展示。</li>
 *   <li>批量收集（非流式）：无 Sink 时事件暂存于 ThreadLocal，由调用方通过 {@link #drain()} 读取。</li>
 * </ul>
 * <p>工具代码无需感知模式差异，统一调用 {@code ToolEventEmitter.emit("xxx")} 即可。</p>
 *
 * @author finch
 * @since 2026-05-22
 */
public final class ToolEventEmitter {

    private static final ThreadLocal<List<String>> EVENTS = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<Sinks.Many<String>> SINK = new ThreadLocal<>();

    private ToolEventEmitter() {}

    /**
     * 绑定实时推送 Sink（流式模式下调用）
     *
     * @param sink SSE 事件发射通道
     */
    public static void setupSink(Sinks.Many<String> sink) {
        SINK.set(sink);
    }

    /**
     * 解绑实时推送 Sink
     */
    public static void teardownSink() {
        SINK.remove();
    }

    /**
     * 发射一个状态事件。
     * <p>有 Sink 时直接推送给前端（实时），无 Sink 时暂存于 ThreadLocal（批量）。</p>
     *
     * @param status 状态文本（如："正在检索知识库 xxx..."）
     */
    public static void emit(String status) {
        Sinks.Many<String> sink = SINK.get();
        if (sink != null) {
            sink.tryEmitNext(status);
        } else {
            EVENTS.get().add(status);
        }
    }

    /**
     * 取出并清空所有累积的事件（非流式模式使用）
     *
     * @return 事件列表（按发射顺序）
     */
    public static List<String> drain() {
        List<String> events = new ArrayList<>(EVENTS.get());
        EVENTS.remove();
        return events;
    }

    /**
     * 清空事件（异常兜底）
     */
    public static void clear() {
        EVENTS.remove();
    }
}

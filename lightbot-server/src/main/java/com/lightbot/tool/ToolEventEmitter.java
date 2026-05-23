package com.lightbot.tool;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具事件发射器（ThreadLocal）
 * <p>在工具执行期间收集中间状态事件，由 ChatService 读取并推送给前端。</p>
 * <p>使用方式：</p>
 * <pre>
 *   ToolEventEmitter.emit("正在检索知识库 xxx...");
 *   // ... 执行检索 ...
 *   ToolEventEmitter.emit("找到 3 条结果");
 *   List&lt;String&gt; events = ToolEventEmitter.drain();
 * </pre>
 *
 * @author finch
 * @since 2026-05-22
 */
public final class ToolEventEmitter {

    private static final ThreadLocal<List<String>> EVENTS = ThreadLocal.withInitial(ArrayList::new);

    private ToolEventEmitter() {}

    /**
     * 发射一个状态事件
     *
     * @param status 状态文本（如："正在检索知识库 xxx..."）
     */
    public static void emit(String status) {
        EVENTS.get().add(status);
    }

    /**
     * 取出并清空所有累积的事件
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

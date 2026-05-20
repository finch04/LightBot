package com.lightbot.log;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 日志事件DTO，用于实时日志监控
 *
 * @author finch
 * @since 2026-05-20
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogEvent {

    /** 时间戳 */
    private long timestamp;

    /** 日志级别：INFO / DEBUG / ERROR / WARN */
    private String level;

    /** 日志来源（Logger 名称） */
    private String logger;

    /** 日志内容 */
    private String message;

    /** 异常堆栈（可选） */
    private String stackTrace;
}

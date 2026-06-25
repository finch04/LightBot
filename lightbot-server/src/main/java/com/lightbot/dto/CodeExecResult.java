package com.lightbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码执行结果
 *
 * @author finch
 * @since 2026-06-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeExecResult {

    /** 是否执行成功 */
    private boolean success;

    /** stdout 输出 */
    private String output;

    /** 返回值（toString） */
    private String returnValue;

    /** 错误信息 */
    private String error;

    /** 执行耗时（毫秒） */
    private long elapsedMs;

    /** 实际使用的语言 */
    private String language;
}

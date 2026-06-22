package com.lightbot.constant;

/**
 * 工具执行结果前缀常量
 *
 * @author finch
 * @since 2026-06-21
 */
public final class ToolResultPrefixes {

    /** 工具执行失败前缀 */
    public static final String FAILURE = "工具执行失败";

    /** 工具不存在前缀 */
    public static final String NOT_FOUND = "工具不存在";

    private ToolResultPrefixes() {}

    /**
     * 构建工具执行失败的 JSON 结果
     * <p>前端通过 {@code _error: true} 判断为错误，渲染错误卡片；
     * LLM 通过 {@code message} 字段理解错误原因。</p>
     *
     * @param message 错误描述
     * @return JSON 字符串，如 {"_error":true,"message":"工具执行失败: xxx"}
     */
    public static String failureJson(String message) {
        // 转义 JSON 特殊字符
        String escaped = message.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return "{\"_error\":true,\"message\":\"" + escaped + "\"}";
    }

    /**
     * 判断工具结果是否为错误
     */
    public static boolean isError(String result) {
        if (result == null) return false;
        // 新格式：JSON 中含 _error:true
        if (result.contains("\"_error\":true")) return true;
        // 旧格式兼容：前缀匹配
        return result.startsWith(FAILURE) || result.startsWith(NOT_FOUND);
    }
}

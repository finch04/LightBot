package com.lightbot.workflow;

/**
 * 工作流节点 data 字段解析工具
 */
public final class WorkflowNodeDataUtils {

    private WorkflowNodeDataUtils() {
    }

    /**
     * 解析 Long 类型 ID（兼容 Number / String）
     */
    public static Long parseLongId(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str) {
            String trimmed = str.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            try {
                return Long.parseLong(trimmed);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 解析非空字符串
     */
    public static String parseString(Object value) {
        if (value == null) {
            return null;
        }
        String str = value.toString().trim();
        return str.isEmpty() ? null : str;
    }
}

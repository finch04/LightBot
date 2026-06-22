package com.lightbot.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 工具调用事件 JSON 生成器
 *
 * @author finch
 * @since 2026-05-23
 */
@Component
@RequiredArgsConstructor
public class ToolEventGenerator {

    private final ObjectMapper objectMapper;

    /** 状态消息前缀，前端通过此前缀识别状态消息 */
    public static final String STATUS_PREFIX = "[STATUS]";
    public static final String DONE_PREFIX = "[DONE]";
    public static final String METADATA_PREFIX = "[METADATA]";
    /** 请求 ID 前缀，前端用于复制并在可观测中检索 */
    public static final String REQUEST_ID_PREFIX = "[REQUEST_ID]";

    /**
     * 生成工具调用状态事件 JSON
     */
    public String toolCallEvent(String toolName, String args, int contentOffset) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "type", "tool_call",
                    "toolName", toolName,
                    "args", args != null ? args : "",
                    "contentOffset", contentOffset));
        } catch (Exception e) {
            return "{\"type\":\"tool_call\",\"toolName\":\"" + toolName + "\",\"contentOffset\":" + contentOffset + "}";
        }
    }

    /**
     * 生成工具结果状态事件 JSON
     */
    public String toolResultEvent(String toolName, String result, int contentOffset) {
        try {
            String truncated = truncateForSse(result);
            return objectMapper.writeValueAsString(Map.of(
                    "type", "tool_result",
                    "toolName", toolName,
                    "result", truncated,
                    "contentOffset", contentOffset));
        } catch (Exception e) {
            return "{\"type\":\"tool_result\",\"toolName\":\"" + toolName + "\",\"contentOffset\":" + contentOffset + "}";
        }
    }

    /**
     * SSE 推送时截断工具结果：JSON 结果不截断（前端需解析），纯文本限制 2000 字符
     */
    private String truncateForSse(String result) {
        if (result == null) return "";
        if (result.startsWith("{") || result.startsWith("[")) return result;
        return result.length() > 2000 ? result.substring(0, 2000) + "..." : result;
    }

    /**
     * 生成工具中间状态事件 JSON（如知识库检索进度）
     */
    public String toolStatusEvent(String message, int contentOffset) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "type", "tool_status",
                    "message", message,
                    "contentOffset", contentOffset));
        } catch (Exception e) {
            return "{\"type\":\"tool_status\",\"message\":\"" + message + "\",\"contentOffset\":" + contentOffset + "}";
        }
    }

    /**
     * 生成工具调用完成标记事件 JSON
     */
    public String toolCompleteEvent(int contentOffset) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "type", "tool_complete",
                    "contentOffset", contentOffset));
        } catch (Exception e) {
            return "{\"type\":\"tool_complete\",\"contentOffset\":" + contentOffset + "}";
        }
    }

    /**
     * 生成思考过程内容事件 JSON
     */
    public String reasoningEvent(String content) {
        try {
            String truncated = content.length() > 8000 ? content.substring(0, 8000) + "..." : content;
            return objectMapper.writeValueAsString(Map.of(
                    "type", "reasoning_content",
                    "content", truncated));
        } catch (Exception e) {
            return "{\"type\":\"reasoning_content\",\"content\":\"\"}";
        }
    }

    /**
     * 工作流节点开始执行事件
     */
    public String workflowNodeStartEvent(String nodeId, String nodeType, String nodeLabel, int contentOffset) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "type", "workflow_node_start",
                    "nodeId", nodeId != null ? nodeId : "",
                    "nodeType", nodeType != null ? nodeType : "",
                    "nodeLabel", nodeLabel != null ? nodeLabel : "",
                    "contentOffset", contentOffset));
        } catch (Exception e) {
            return "{\"type\":\"workflow_node_start\",\"nodeId\":\"" + nodeId + "\",\"contentOffset\":" + contentOffset + "}";
        }
    }

    /**
     * 工作流节点执行完成事件
     */
    public String workflowNodeCompleteEvent(String nodeId, String nodeType, String nodeLabel,
                                                   String message, boolean success, int contentOffset) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "type", "workflow_node_complete",
                    "nodeId", nodeId != null ? nodeId : "",
                    "nodeType", nodeType != null ? nodeType : "",
                    "nodeLabel", nodeLabel != null ? nodeLabel : "",
                    "message", message != null ? message : "执行完成",
                    "success", success,
                    "contentOffset", contentOffset));
        } catch (Exception e) {
            return "{\"type\":\"workflow_node_complete\",\"nodeId\":\"" + nodeId + "\",\"contentOffset\":" + contentOffset + "}";
        }
    }

    /**
     * 工作流全部节点执行完成标记
     */
    public String workflowCompleteEvent(int contentOffset) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "type", "workflow_complete",
                    "contentOffset", contentOffset));
        } catch (Exception e) {
            return "{\"type\":\"workflow_complete\",\"contentOffset\":" + contentOffset + "}";
        }
    }

    /**
     * Skill 启用事件（本轮对话 Agent 已绑定的 Skill 清单）
     *
     * @param skills 元素含 name、displayName、slug、builtin
     */
    public String skillActiveEvent(java.util.List<java.util.Map<String, Object>> skills) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "type", "skill_active",
                    "skills", skills != null ? skills : java.util.List.of(),
                    "contentOffset", 0));
        } catch (Exception e) {
            return "{\"type\":\"skill_active\",\"skills\":[],\"contentOffset\":0}";
        }
    }

    /**
     * SubAgent 委派开始
     */
    public String subagentCallEvent(String subagentName, String displayName, String task, int contentOffset) {
        try {
            String truncated = task != null && task.length() > 500 ? task.substring(0, 500) + "..." : (task != null ? task : "");
            return objectMapper.writeValueAsString(Map.of(
                    "type", "subagent_call",
                    "subagentName", subagentName != null ? subagentName : "",
                    "displayName", displayName != null ? displayName : subagentName,
                    "task", truncated,
                    "contentOffset", contentOffset));
        } catch (Exception e) {
            return "{\"type\":\"subagent_call\",\"subagentName\":\"" + subagentName + "\",\"contentOffset\":" + contentOffset + "}";
        }
    }

    /**
     * SubAgent 委派完成
     */
    public String subagentResultEvent(String subagentName, String displayName, String result, int contentOffset) {
        try {
            String truncated = result != null && result.length() > 2000 ? result.substring(0, 2000) + "..." : (result != null ? result : "");
            return objectMapper.writeValueAsString(Map.of(
                    "type", "subagent_result",
                    "subagentName", subagentName != null ? subagentName : "",
                    "displayName", displayName != null ? displayName : subagentName,
                    "result", truncated,
                    "contentOffset", contentOffset));
        } catch (Exception e) {
            return "{\"type\":\"subagent_result\",\"subagentName\":\"" + subagentName + "\",\"contentOffset\":" + contentOffset + "}";
        }
    }

    /**
     * 敏感词拦截事件
     *
     * @param scope   拦截范围：user_input / ai_output
     * @param message 拦截提示文本
     */
    public String sensitiveBlockEvent(String scope, String message) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "type", "sensitive_block",
                    "scope", scope,
                    "message", message != null ? message : ""));
        } catch (Exception e) {
            return "{\"type\":\"sensitive_block\",\"scope\":\"" + scope + "\",\"message\":\"\"}";
        }
    }
}

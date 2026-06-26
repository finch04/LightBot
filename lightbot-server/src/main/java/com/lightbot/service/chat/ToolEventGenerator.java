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

    /** 安全回退 JSON：objectMapper 异常时使用，确保值被正确转义 */
    private String safeFallbackJson(Map<String, Object> fields) {
        try {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (var entry : fields.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":");
                sb.append(objectMapper.writeValueAsString(entry.getValue()));
                first = false;
            }
            return sb.append("}").toString();
        } catch (Exception e) {
            return "{\"type\":\"error\",\"message\":\"事件生成失败\"}";
        }
    }

    /**
     * 生成工具调用状态事件 JSON
     *
     * @param toolName    工具标识
     * @param displayName 工具显示名称（中文），可为 null
     * @param args        调用参数
     * @param contentOffset 内容偏移
     */
    public String toolCallEvent(String toolName, String displayName, String args, int contentOffset) {
        try {
            Map<String, Object> evt = new java.util.LinkedHashMap<>();
            evt.put("type", "tool_call");
            evt.put("toolName", toolName);
            if (displayName != null && !displayName.isEmpty()) {
                evt.put("displayName", displayName);
            }
            evt.put("args", args != null ? args : "");
            evt.put("contentOffset", contentOffset);
            return objectMapper.writeValueAsString(evt);
        } catch (Exception e) {
            return safeFallbackJson(Map.of("type", "tool_call", "toolName", toolName != null ? toolName : "", "contentOffset", contentOffset));
        }
    }

    /**
     * 生成工具结果状态事件 JSON
     *
     * @param toolName    工具标识
     * @param displayName 工具显示名称（中文），可为 null
     * @param result      执行结果
     * @param contentOffset 内容偏移
     */
    public String toolResultEvent(String toolName, String displayName, String result, int contentOffset) {
        try {
            String truncated = truncateForSse(result);
            Map<String, Object> evt = new java.util.LinkedHashMap<>();
            evt.put("type", "tool_result");
            evt.put("toolName", toolName);
            if (displayName != null && !displayName.isEmpty()) {
                evt.put("displayName", displayName);
            }
            evt.put("result", truncated);
            evt.put("contentOffset", contentOffset);
            return objectMapper.writeValueAsString(evt);
        } catch (Exception e) {
            return safeFallbackJson(Map.of("type", "tool_result", "toolName", toolName != null ? toolName : "", "contentOffset", contentOffset));
        }
    }

    /**
     * SSE 推送时截断工具结果：JSON 结果不截断（前端需解析），纯文本限制 2000 字符
     */
    public String truncateForSse(String result) {
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
            return safeFallbackJson(Map.of("type", "tool_status", "message", message != null ? message : "", "contentOffset", contentOffset));
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
            return safeFallbackJson(Map.of("type", "tool_complete", "contentOffset", contentOffset));
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
            return safeFallbackJson(Map.of("type", "reasoning_content", "content", ""));
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
            return safeFallbackJson(Map.of("type", "workflow_node_start", "nodeId", nodeId != null ? nodeId : "", "contentOffset", contentOffset));
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
            return safeFallbackJson(Map.of("type", "workflow_node_complete", "nodeId", nodeId != null ? nodeId : "", "contentOffset", contentOffset));
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
            return safeFallbackJson(Map.of("type", "workflow_complete", "contentOffset", contentOffset));
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
            return safeFallbackJson(Map.of("type", "subagent_call", "subagentName", subagentName != null ? subagentName : "", "contentOffset", contentOffset));
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
            return safeFallbackJson(Map.of("type", "subagent_result", "subagentName", subagentName != null ? subagentName : "", "contentOffset", contentOffset));
        }
    }

    /**
     * SubAgent 流式 token 事件
     */
    public String subagentTokenEvent(String subagentName, String token, int contentOffset) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "type", "subagent_token",
                    "subagentName", subagentName != null ? subagentName : "",
                    "content", token != null ? token : "",
                    "contentOffset", contentOffset));
        } catch (Exception e) {
            return safeFallbackJson(Map.of("type", "subagent_token", "subagentName", subagentName != null ? subagentName : "", "contentOffset", contentOffset));
        }
    }

    /**
     * SubAgent 子工具调用事件
     */
    public String subagentToolCallEvent(String subagentName, String toolName, int contentOffset) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "type", "subagent_tool_call",
                    "subagentName", subagentName != null ? subagentName : "",
                    "toolName", toolName != null ? toolName : "",
                    "contentOffset", contentOffset));
        } catch (Exception e) {
            return safeFallbackJson(Map.of("type", "subagent_tool_call", "subagentName", subagentName != null ? subagentName : "", "contentOffset", contentOffset));
        }
    }

    /**
     * SubAgent 子工具结果事件
     */
    public String subagentToolResultEvent(String subagentName, String result, int contentOffset) {
        try {
            String truncated = result != null && result.length() > 500 ? result.substring(0, 500) + "..." : (result != null ? result : "");
            return objectMapper.writeValueAsString(Map.of(
                    "type", "subagent_tool_result",
                    "subagentName", subagentName != null ? subagentName : "",
                    "content", truncated,
                    "contentOffset", contentOffset));
        } catch (Exception e) {
            return safeFallbackJson(Map.of("type", "subagent_tool_result", "subagentName", subagentName != null ? subagentName : "", "contentOffset", contentOffset));
        }
    }

    /**
     * 流式错误事件：LLM 调用中断、工具执行异常等
     *
     * @param message 用户友好的错误提示
     * @param code    错误码（LLM_ERROR / TOOL_ERROR / TIMEOUT 等）
     */
    public String errorEvent(String message, String code) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "type", "error",
                    "message", message != null ? message : "未知错误",
                    "code", code != null ? code : "UNKNOWN"));
        } catch (Exception e) {
            return safeFallbackJson(Map.of("type", "error", "message", message != null ? message : "", "code", code != null ? code : ""));
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
            return safeFallbackJson(Map.of("type", "sensitive_block", "scope", scope != null ? scope : "", "message", ""));
        }
    }

    /**
     * 生成带消息ID的 [DONE] 事件
     * <p>格式：[DONE]{"userMessageId":"...","assistantMessageId":"..."}</p>
     *
     * @param userMessageId      用户消息ID
     * @param assistantMessageId AI回复消息ID
     */
    public String doneWithMetadata(Long userMessageId, Long assistantMessageId) {
        return doneWithMetadata(userMessageId, assistantMessageId, 0);
    }

    public String doneWithMetadata(Long userMessageId, Long assistantMessageId, long totalTokens) {
        return doneWithMetadata(userMessageId, assistantMessageId, totalTokens, null);
    }

    /**
     * 生成带消息ID和完整 metadata 的 [DONE] 事件
     *
     * @param userMessageId      用户消息ID
     * @param assistantMessageId AI回复消息ID
     * @param totalTokens        总Token消耗
     * @param fullMetadata       持久化metadata JSON（含 ragReferences、reasoningContent 等），可为null
     */
    public String doneWithMetadata(Long userMessageId, Long assistantMessageId, long totalTokens, String fullMetadata) {
        try {
            Map<String, Object> meta = new java.util.LinkedHashMap<>();
            if (userMessageId != null) {
                meta.put("userMessageId", userMessageId.toString());
            }
            if (assistantMessageId != null) {
                meta.put("assistantMessageId", assistantMessageId.toString());
            }
            if (totalTokens > 0) {
                meta.put("totalTokens", totalTokens);
            }
            // 合并完整 metadata（ragReferences、reasoningContent、requestId 等）
            if (fullMetadata != null && !fullMetadata.isBlank()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> extra = objectMapper.readValue(fullMetadata, Map.class);
                meta.putAll(extra);
            }
            if (meta.isEmpty()) {
                return DONE_PREFIX;
            }
            return DONE_PREFIX + objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            return DONE_PREFIX;
        }
    }
}

package com.lightbot.subagent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SubAgent 线程管理器
 * <p>负责子代理线程 ID 的确定性生成和消息历史的 Redis 持久化，支持续跑机制。</p>
 *
 * @author finch
 * @since 2026-06-25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubAgentThreadManager {

    private static final String MSG_KEY_PREFIX = "subagent:msg:";
    private static final long TTL_SECONDS = 24 * 3600; // 24h
    private static final int MAX_MESSAGES = 100;

    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;

    /**
     * 生成确定性子线程 ID（与 Yuxi 的 make_child_thread_id 对齐）
     *
     * @param parentThreadId 父 Agent 线程 ID
     * @param agentName      子代理名称
     * @param toolCallId     工具调用 ID（requestId）
     * @return 确定性线程 ID
     */
    public static String makeChildThreadId(String parentThreadId, String agentName, String toolCallId) {
        String input = (parentThreadId != null ? parentThreadId : "")
                + ":" + (agentName != null ? agentName : "")
                + ":" + (toolCallId != null ? toolCallId : "");
        String digest = DigestUtils.md5DigestAsHex(input.getBytes(StandardCharsets.UTF_8));
        return "subagent_" + digest;
    }

    /**
     * 加载已有消息历史（续跑时使用）
     *
     * @param threadId 线程 ID
     * @return 消息列表，不存在时返回空列表
     */
    public List<Message> loadMessages(String threadId) {
        if (threadId == null) {
            return new ArrayList<>();
        }
        String json = redisUtil.get(MSG_KEY_PREFIX + threadId);
        if (json == null) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(json, new TypeReference<>() {});
            return deserializeMessages(raw);
        } catch (Exception e) {
            log.warn("[SubAgentThread] 消息反序列化失败: threadId={}, error={}", threadId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 保存消息历史
     *
     * @param threadId 线程 ID
     * @param messages 消息列表
     */
    public void saveMessages(String threadId, List<Message> messages) {
        if (threadId == null || messages == null) {
            return;
        }
        try {
            // 截断过长的消息列表，保留 SystemMessage + 最近消息
            List<Message> toSave = truncateMessages(messages);
            List<Map<String, Object>> serialized = serializeMessages(toSave);
            String json = objectMapper.writeValueAsString(serialized);
            redisUtil.set(MSG_KEY_PREFIX + threadId, json, TTL_SECONDS);
        } catch (Exception e) {
            log.warn("[SubAgentThread] 消息保存失败: threadId={}, error={}", threadId, e.getMessage());
        }
    }

    /**
     * 检查线程是否存在
     */
    public boolean threadExists(String threadId) {
        if (threadId == null) {
            return false;
        }
        return redisUtil.exists(MSG_KEY_PREFIX + threadId);
    }

    /**
     * 截断消息列表：保留首条 SystemMessage + 最近的 MAX_MESSAGES-1 条
     */
    private List<Message> truncateMessages(List<Message> messages) {
        if (messages.size() <= MAX_MESSAGES) {
            return messages;
        }
        List<Message> result = new ArrayList<>();
        // 保留首条 SystemMessage
        if (!messages.isEmpty() && messages.get(0) instanceof SystemMessage) {
            result.add(messages.get(0));
        }
        // 保留最近的消息
        int start = messages.size() - (MAX_MESSAGES - result.size());
        for (int i = Math.max(start, result.size()); i < messages.size(); i++) {
            result.add(messages.get(i));
        }
        return result;
    }

    /**
     * 序列化消息列表为可存储的 Map 列表
     */
    private List<Map<String, Object>> serializeMessages(List<Message> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Message msg : messages) {
            Map<String, Object> entry = new HashMap<>();
            if (msg instanceof SystemMessage sm) {
                entry.put("type", "system");
                entry.put("content", sm.getText());
            } else if (msg instanceof UserMessage um) {
                entry.put("type", "user");
                entry.put("content", um.getText());
            } else if (msg instanceof AssistantMessage am) {
                entry.put("type", "assistant");
                entry.put("content", am.getText());
                if (am.hasToolCalls()) {
                    List<Map<String, Object>> tcList = new ArrayList<>();
                    for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
                        Map<String, Object> tcMap = new HashMap<>();
                        tcMap.put("id", tc.id());
                        tcMap.put("name", tc.name());
                        tcMap.put("arguments", tc.arguments());
                        tcList.add(tcMap);
                    }
                    entry.put("toolCalls", tcList);
                }
            } else if (msg instanceof ToolResponseMessage trm) {
                entry.put("type", "tool_response");
                List<Map<String, Object>> respList = new ArrayList<>();
                for (ToolResponseMessage.ToolResponse tr : trm.getResponses()) {
                    Map<String, Object> respMap = new HashMap<>();
                    respMap.put("id", tr.id());
                    respMap.put("name", tr.name());
                    respMap.put("responseData", tr.responseData());
                    respList.add(respMap);
                }
                entry.put("responses", respList);
            }
            result.add(entry);
        }
        return result;
    }

    /**
     * 反序列化 Map 列表为 Spring AI Message 列表
     */
    private List<Message> deserializeMessages(List<Map<String, Object>> raw) {
        List<Message> messages = new ArrayList<>();
        for (Map<String, Object> entry : raw) {
            String type = (String) entry.get("type");
            if (type == null) continue;
            switch (type) {
                case "system" -> messages.add(new SystemMessage((String) entry.get("content")));
                case "user" -> messages.add(new UserMessage((String) entry.get("content")));
                case "assistant" -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> tcList = (List<Map<String, Object>>) entry.get("toolCalls");
                    List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
                    if (tcList != null) {
                        for (Map<String, Object> tc : tcList) {
                            toolCalls.add(new AssistantMessage.ToolCall(
                                    (String) tc.get("id"),
                                    "function",
                                    (String) tc.get("name"),
                                    (String) tc.get("arguments")));
                        }
                    }
                    AssistantMessage am = AssistantMessage.builder()
                            .content((String) entry.get("content"))
                            .toolCalls(toolCalls)
                            .build();
                    messages.add(am);
                }
                case "tool_response" -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> respList = (List<Map<String, Object>>) entry.get("responses");
                    if (respList != null) {
                        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
                        for (Map<String, Object> resp : respList) {
                            responses.add(new ToolResponseMessage.ToolResponse(
                                    (String) resp.get("id"),
                                    (String) resp.get("name"),
                                    (String) resp.get("responseData")));
                        }
                        messages.add(ToolResponseMessage.builder().responses(responses).build());
                    }
                }
            }
        }
        return messages;
    }
}

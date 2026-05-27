package com.lightbot.util;

import com.lightbot.dto.ChatAttachmentDTO;
import com.lightbot.dto.ChatRequest;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将发给模型的消息序列化为 Trace 可观测结构（完整文本 + 附件元数据，不截断正文）
 */
public final class LlmTraceMessageSerializer {

    private LlmTraceMessageSerializer() {
    }

    /**
     * @param messages     实际发给 LLM 的消息列表
     * @param request      本轮对话请求（用于附件 previewUrl 等）
     * @param lastUserHasAttachments 当前轮用户消息是否带附件（用于对齐 media 与 DTO）
     */
    public static List<Map<String, Object>> toTraceMessages(
            List<Message> messages,
            ChatRequest request,
            boolean lastUserHasAttachments) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<ChatAttachmentDTO> currentAttachments = request != null && request.getAttachments() != null
                ? request.getAttachments() : List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        int userIndexFromEnd = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage) {
                userIndexFromEnd = i;
                break;
            }
        }
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            boolean isCurrentUser = lastUserHasAttachments && i == userIndexFromEnd;
            result.add(toTraceMessageItem(msg, isCurrentUser ? currentAttachments : List.of()));
        }
        return result;
    }

    public static Map<String, Object> toTraceMessageItem(Message msg, List<ChatAttachmentDTO> attachmentHints) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("role", msg.getMessageType().getValue());
        item.put("content", extractText(msg));
        if (msg instanceof UserMessage um) {
            List<Map<String, Object>> mediaTrace = traceMediaList(um, attachmentHints);
            if (!mediaTrace.isEmpty()) {
                item.put("media", mediaTrace);
            }
        }
        return item;
    }

    private static String extractText(Message msg) {
        if (msg instanceof SystemMessage sm) {
            return sm.getText();
        }
        if (msg instanceof UserMessage um) {
            return um.getText();
        }
        if (msg instanceof AssistantMessage am) {
            return am.getText();
        }
        return msg.toString();
    }

    private static List<Map<String, Object>> traceMediaList(UserMessage um, List<ChatAttachmentDTO> hints) {
        if (um.getMedia() == null || um.getMedia().isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        List<Media> medias = um.getMedia();
        for (int i = 0; i < medias.size(); i++) {
            Media media = medias.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            if (media.getMimeType() != null) {
                m.put("mimeType", media.getMimeType().toString());
            }
            ChatAttachmentDTO hint = hints != null && i < hints.size() ? hints.get(i) : null;
            if (hint != null) {
                if (hint.getType() != null) {
                    m.put("type", hint.getType());
                }
                if (hint.getFileName() != null) {
                    m.put("fileName", hint.getFileName());
                }
                if (hint.getPreviewUrl() != null) {
                    m.put("previewUrl", hint.getPreviewUrl());
                }
                if (hint.getObjectKey() != null) {
                    m.put("objectKey", hint.getObjectKey());
                }
                if (hint.getMimeType() != null) {
                    m.put("mimeType", hint.getMimeType());
                }
            } else {
                Object data = media.getData();
                if (data != null) {
                    String uriStr = data.toString();
                    if (uriStr.startsWith("data:")) {
                        m.put("inlineData", true);
                        m.put("approxChars", uriStr.length());
                    } else {
                        m.put("url", uriStr);
                    }
                }
            }
            result.add(m);
        }
        return result;
    }
}

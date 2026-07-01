package com.lightbot.service;

import com.lightbot.constant.ChatAttachmentConstants;
import com.lightbot.dto.ChatAttachmentDTO;
import com.lightbot.util.MinioUtil;
import com.lightbot.util.SessionStoragePath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 对话文档附件解析文本：仅存 MinIO，不写入消息 metadata。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAttachmentParsedService {

    private final MinioUtil minioUtil;

    /**
     * 上传时落盘解析 Markdown。
     */
    public void storeParsed(Long sessionId, Long agentId, String attachmentId, String fileName, String parsedText) {
        if (parsedText == null || parsedText.isBlank()) {
            return;
        }
        String key = resolveParsedObjectKey(sessionId, agentId, attachmentId, fileName, null);
        uploadString(key, parsedText);
    }

    /**
     * temp 附件迁移到会话目录时，同步复制解析产物。
     */
    public void relocateParsedIfPresent(Long sessionId, ChatAttachmentDTO att, String oldObjectKey) {
        if (att == null || sessionId == null || oldObjectKey == null) {
            return;
        }
        String fileName = att.getFileName() != null ? att.getFileName() : "attachment.bin";
        Long agentId = SessionStoragePath.extractAgentIdFromTempObjectKey(oldObjectKey);
        if (agentId == null || att.getId() == null) {
            return;
        }
        String from = SessionStoragePath.tempParsedObjectKey(agentId, att.getId(), fileName);
        String to = SessionStoragePath.inputParsedObjectKey(sessionId, fileName);
        try {
            if (minioUtil.exists(from) && !minioUtil.exists(to)) {
                minioUtil.copyObject(from, to);
            }
        } catch (Exception e) {
            log.warn("[ChatAttachment] 迁移解析产物失败: from={}, to={}, error={}", from, to, e.getMessage());
        }
    }

    /**
     * 文档附件是否已有 MinIO 解析产物。
     */
    public boolean hasParsedContent(ChatAttachmentDTO att, Long sessionId) {
        String key = resolveParsedObjectKey(sessionId, null, att != null ? att.getId() : null,
                att != null ? att.getFileName() : null, att != null ? att.getObjectKey() : null);
        return key != null && minioUtil.exists(key);
    }

    /**
     * 将文档解析文本与用户问题合并为模型输入。
     */
    public String wrapUserMessage(String userQuestion, List<ChatAttachmentDTO> documents, Long sessionId) {
        if (documents == null || documents.isEmpty()) {
            return userQuestion != null ? userQuestion : "";
        }
        String question = (userQuestion != null && !userQuestion.isBlank())
                ? userQuestion.trim()
                : "请根据上传的文件内容回答。";

        StringBuilder filesBlock = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            ChatAttachmentDTO doc = documents.get(i);
            String name = doc.getFileName() != null ? doc.getFileName() : ("文件" + (i + 1));
            String content = loadParsedText(doc, sessionId);
            if (content == null || content.isBlank()) {
                content = "（未能解析出文本内容）";
            } else if (content.length() >= ChatAttachmentConstants.MAX_PARSED_TEXT_CHARS) {
                content = content + "\n\n（注：文件内容过长，以上为截断后的节选）";
            }
            filesBlock.append("---\n【").append(name).append("】\n").append(content).append("\n");
        }

        return """
                用户上传了文件，请先阅读文件内容，再结合用户问题作答。若文件与问题无关，请说明后仅回答用户问题。

                【上传文件内容】
                %s
                【用户问题】
                %s
                """.formatted(filesBlock.toString().trim(), question);
    }

    private String loadParsedText(ChatAttachmentDTO att, Long sessionId) {
        String key = resolveParsedObjectKey(sessionId, null, att.getId(), att.getFileName(), att.getObjectKey());
        if (key == null || !minioUtil.exists(key)) {
            return "";
        }
        try (InputStream in = minioUtil.download(key)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[ChatAttachment] 读取解析产物失败: key={}, error={}", key, e.getMessage());
            return "";
        }
    }

    private String resolveParsedObjectKey(Long sessionId, Long agentId, String attachmentId,
                                          String fileName, String objectKey) {
        if (fileName == null || fileName.isBlank()) {
            fileName = "attachment.bin";
        }
        if (objectKey != null && objectKey.startsWith(SessionStoragePath.SESSIONS_PREFIX)) {
            Long sid = sessionId != null ? sessionId : SessionStoragePath.extractSessionIdFromObjectKey(objectKey);
            if (sid != null) {
                return SessionStoragePath.inputParsedObjectKey(sid, fileName);
            }
        }
        if (objectKey != null && objectKey.startsWith("chat/")) {
            Long aid = agentId != null ? agentId : SessionStoragePath.extractAgentIdFromTempObjectKey(objectKey);
            if (aid != null && attachmentId != null) {
                return SessionStoragePath.tempParsedObjectKey(aid, attachmentId, fileName);
            }
        }
        if (sessionId != null) {
            return SessionStoragePath.inputParsedObjectKey(sessionId, fileName);
        }
        if (agentId != null && attachmentId != null) {
            return SessionStoragePath.tempParsedObjectKey(agentId, attachmentId, fileName);
        }
        return null;
    }

    private void uploadString(String key, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        try {
            minioUtil.upload(new java.io.ByteArrayInputStream(bytes), key, bytes.length, "text/markdown");
        } catch (Exception e) {
            log.warn("[ChatAttachment] 解析产物落盘失败: key={}, error={}", key, e.getMessage());
        }
    }
}

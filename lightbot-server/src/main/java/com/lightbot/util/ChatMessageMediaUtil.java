package com.lightbot.util;

import com.lightbot.dto.ChatAttachmentDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 构建带图片/视频的多模态 UserMessage（OpenAI 兼容 data URL）
 */
@Slf4j
public final class ChatMessageMediaUtil {

    private ChatMessageMediaUtil() {
    }

    /**
     * 构建用户消息：文本 + 附件（base64 data URL）
     */
    public static UserMessage buildUserMessage(String text, List<ChatAttachmentDTO> attachments,
                                               MinioUtil minioUtil) {
        if (attachments == null || attachments.isEmpty()) {
            return new UserMessage(text != null ? text : "");
        }
        List<Media> mediaList = new ArrayList<>();
        for (ChatAttachmentDTO att : attachments) {
            if (att.getObjectKey() == null || att.getObjectKey().isBlank()) {
                continue;
            }
            try {
                byte[] bytes = minioUtil.downloadBytes(att.getObjectKey());
                String mime = att.getMimeType() != null ? att.getMimeType() : "application/octet-stream";
                String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
                mediaList.add(new Media(MimeTypeUtils.parseMimeType(mime), URI.create(dataUrl)));
            } catch (Exception e) {
                log.warn("[ChatMedia] 读取附件失败: key={}, error={}", att.getObjectKey(), e.getMessage());
            }
        }
        if (mediaList.isEmpty()) {
            return new UserMessage(text != null ? text : "");
        }
        return UserMessage.builder()
                .text(text != null ? text : "请根据附件内容回答。")
                .media(mediaList)
                .build();
    }
}

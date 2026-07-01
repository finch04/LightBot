package com.lightbot.util;

import com.lightbot.common.BizException;
import com.lightbot.dto.ChatAttachmentDTO;
import com.lightbot.enums.ErrorCode;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话文档附件：类型判断与多模态混传校验
 */
public final class ChatDocumentMessageUtil {

    private ChatDocumentMessageUtil() {
    }

    public static boolean isDocumentAttachment(ChatAttachmentDTO att) {
        return att != null && "document".equals(att.getType());
    }

    /** 多模态附件：仅图片/视频走 Media，与文档解析分流 */
    public static boolean isMediaAttachment(ChatAttachmentDTO att) {
        if (att == null || att.getType() == null) {
            return false;
        }
        return "image".equals(att.getType()) || "video".equals(att.getType());
    }

    public static List<ChatAttachmentDTO> filterDocuments(List<ChatAttachmentDTO> attachments) {
        if (attachments == null) {
            return List.of();
        }
        return attachments.stream().filter(ChatDocumentMessageUtil::isDocumentAttachment).collect(Collectors.toList());
    }

    public static List<ChatAttachmentDTO> filterMedia(List<ChatAttachmentDTO> attachments) {
        if (attachments == null) {
            return List.of();
        }
        return attachments.stream().filter(ChatDocumentMessageUtil::isMediaAttachment).collect(Collectors.toList());
    }

    /**
     * 同一条消息禁止同时包含图片与视频（可与文档混传）；避免 MiMo 等将文档当 image_url 报错
     */
    public static void validateMediaMix(List<ChatAttachmentDTO> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        boolean hasImage = false;
        boolean hasVideo = false;
        for (ChatAttachmentDTO att : attachments) {
            if (att == null || att.getType() == null) {
                continue;
            }
            if ("image".equals(att.getType())) {
                hasImage = true;
            } else if ("video".equals(att.getType())) {
                hasVideo = true;
            }
        }
        if (hasImage && hasVideo) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                    "同一条消息不能同时上传图片和视频，可与文档附件搭配使用");
        }
    }
}

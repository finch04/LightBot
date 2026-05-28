package com.lightbot.util;

import com.lightbot.common.BizException;
import com.lightbot.dto.ChatAttachmentDTO;
import com.lightbot.enums.ErrorCode;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话文档附件：将 Tika 解析结果拼入用户消息文本（非多模态，走纯文本上下文）
 */
public final class ChatDocumentMessageUtil {

    private ChatDocumentMessageUtil() {
    }

    /**
     * 将文档解析文本与用户问题合并为模型输入
     */
    public static String wrapUserMessage(String userQuestion, List<ChatAttachmentDTO> documents) {
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
            String content = doc.getParsedText() != null ? doc.getParsedText() : "（未能解析出文本内容）";
            if (Boolean.TRUE.equals(doc.getParsedTextTruncated())) {
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

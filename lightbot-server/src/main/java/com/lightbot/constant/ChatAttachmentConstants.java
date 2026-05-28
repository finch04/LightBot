package com.lightbot.constant;

import java.util.Map;
import java.util.Set;

/**
 * 对话附件上传：允许的格式与大小上限（前后端保持一致）
 */
public final class ChatAttachmentConstants {

    private ChatAttachmentConstants() {
    }

    /** 单条用户消息最多附件数（图片+视频合计） */
    public static final int MAX_ATTACHMENTS_PER_MESSAGE = 3;

    // 图片1MB内
    public static final long MAX_IMAGE_BYTES = 1L * 1024 * 1024;
    // 视频5MB内
    public static final long MAX_VIDEO_BYTES = 5L * 1024 * 1024;

    public static final Set<String> IMAGE_MIMES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");
    public static final Set<String> VIDEO_MIMES = Set.of(
            "video/mp4", "video/webm", "video/quicktime");

    public static final Set<String> IMAGE_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".webp", ".gif");
    public static final Set<String> VIDEO_EXTENSIONS = Set.of(
            ".mp4", ".webm", ".mov");

    /** 文档附件：单文件最大 5MB */
    public static final long MAX_DOCUMENT_BYTES = 5L * 1024 * 1024;

    /** Tika 解析后注入模型的最大字符数（约 8k–12k token 量级，可按需调整） */
    public static final int MAX_PARSED_TEXT_CHARS = 32_000;

    /** 与知识库 TikaUtil 对齐的可读文档扩展名（含点） */
    public static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
            ".md", ".txt", ".pdf", ".doc", ".docx", ".ppt", ".pptx",
            ".xls", ".xlsx", ".csv", ".html", ".htm");

    public static final Set<String> DOCUMENT_MIMES = Set.of(
            "text/plain", "text/markdown", "text/html", "text/csv",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private static final Map<String, String> EXT_TO_MIME = Map.ofEntries(
            Map.entry(".jpg", "image/jpeg"),
            Map.entry(".jpeg", "image/jpeg"),
            Map.entry(".png", "image/png"),
            Map.entry(".webp", "image/webp"),
            Map.entry(".gif", "image/gif"),
            Map.entry(".mp4", "video/mp4"),
            Map.entry(".webm", "video/webm"),
            Map.entry(".mov", "video/quicktime"),
            Map.entry(".md", "text/markdown"),
            Map.entry(".txt", "text/plain"),
            Map.entry(".pdf", "application/pdf"),
            Map.entry(".doc", "application/msword"),
            Map.entry(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry(".ppt", "application/vnd.ms-powerpoint"),
            Map.entry(".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry(".xls", "application/vnd.ms-excel"),
            Map.entry(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry(".csv", "text/csv"),
            Map.entry(".html", "text/html"),
            Map.entry(".htm", "text/html")
    );

    /**
     * 截断解析文本并标记是否被截断
     */
    public static String truncateParsedText(String text, boolean[] truncatedOut) {
        if (text == null) {
            if (truncatedOut != null && truncatedOut.length > 0) {
                truncatedOut[0] = false;
            }
            return "";
        }
        if (text.length() <= MAX_PARSED_TEXT_CHARS) {
            if (truncatedOut != null && truncatedOut.length > 0) {
                truncatedOut[0] = false;
            }
            return text;
        }
        if (truncatedOut != null && truncatedOut.length > 0) {
            truncatedOut[0] = true;
        }
        return text.substring(0, MAX_PARSED_TEXT_CHARS);
    }

    public static String normalizeExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot >= filename.length() - 1) {
            return "";
        }
        return filename.substring(dot).toLowerCase();
    }

    public static String mimeFromExtension(String ext) {
        return EXT_TO_MIME.get(ext);
    }
}

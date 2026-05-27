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

    public static final long MAX_IMAGE_BYTES = 4L * 1024 * 1024;
    public static final long MAX_VIDEO_BYTES = 20L * 1024 * 1024;

    public static final Set<String> IMAGE_MIMES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");
    public static final Set<String> VIDEO_MIMES = Set.of(
            "video/mp4", "video/webm", "video/quicktime");

    public static final Set<String> IMAGE_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".webp", ".gif");
    public static final Set<String> VIDEO_EXTENSIONS = Set.of(
            ".mp4", ".webm", ".mov");

    private static final Map<String, String> EXT_TO_MIME = Map.ofEntries(
            Map.entry(".jpg", "image/jpeg"),
            Map.entry(".jpeg", "image/jpeg"),
            Map.entry(".png", "image/png"),
            Map.entry(".webp", "image/webp"),
            Map.entry(".gif", "image/gif"),
            Map.entry(".mp4", "video/mp4"),
            Map.entry(".webm", "video/webm"),
            Map.entry(".mov", "video/quicktime")
    );

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

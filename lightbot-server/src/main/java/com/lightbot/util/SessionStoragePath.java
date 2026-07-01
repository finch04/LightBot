package com.lightbot.util;

import java.util.Locale;

/**
 * 会话级 MinIO 路径规范（参考 Yuxi：每会话一个目录，inputs / outputs / workspace 分区）。
 * <p>所有会话文件统一落在 {@code sessions/{sessionId}/} 下：</p>
 * <pre>
 * sessions/{sessionId}/
 * ├── inputs/                 # 用户上传
 * │   └── parsed/             # 文档解析后的 Markdown
 * ├── outputs/                # AI 正式产出
 * │   ├── images/             # image_generation 工具
 * │   └── files/              # present_artifacts 交付物
 * └── workspace/              # Agent 沙盒工作区（sandbox_write_file / execute_code）
 * </pre>
 *
 * @author finch
 * @since 2026-06-30
 */
public final class SessionStoragePath {

    public static final String SESSIONS_PREFIX = "sessions/";

    /** 用户输入目录名（对应 Yuxi uploads/） */
    public static final String INPUTS_DIR = "inputs";
    /** AI 产出目录名 */
    public static final String OUTPUTS_DIR = "outputs";
    /** Agent 工作区目录名 */
    public static final String WORKSPACE_DIR = "workspace";
    /** 文档解析产物子目录名 */
    public static final String PARSED_DIR = "parsed";
    /** AI 生图子目录名 */
    public static final String IMAGES_DIR = "images";
    /** AI 交付物子目录名 */
    public static final String FILES_DIR = "files";

    private SessionStoragePath() {
    }

    /** 会话根目录：sessions/{sessionId}/ */
    public static String sessionRoot(Long sessionId) {
        return SESSIONS_PREFIX + sessionId + "/";
    }

    /** 用户上传根：sessions/{sessionId}/inputs/ */
    public static String inputsPrefix(Long sessionId) {
        return sessionRoot(sessionId) + INPUTS_DIR + "/";
    }

    /** 文档解析产物根：sessions/{sessionId}/inputs/parsed/ */
    public static String inputsParsedPrefix(Long sessionId) {
        return inputsPrefix(sessionId) + PARSED_DIR + "/";
    }

    /** AI 产出根：sessions/{sessionId}/outputs/ */
    public static String outputsPrefix(Long sessionId) {
        return sessionRoot(sessionId) + OUTPUTS_DIR + "/";
    }

    /** AI 生图根：sessions/{sessionId}/outputs/images/ */
    public static String outputsImagesPrefix(Long sessionId) {
        return outputsPrefix(sessionId) + IMAGES_DIR + "/";
    }

    /** AI 交付物根：sessions/{sessionId}/outputs/files/ */
    public static String outputsFilesPrefix(Long sessionId) {
        return outputsPrefix(sessionId) + FILES_DIR + "/";
    }

    /** Agent 工作区根：sessions/{sessionId}/workspace/ */
    public static String workspacePrefix(Long sessionId) {
        return sessionRoot(sessionId) + WORKSPACE_DIR + "/";
    }

    /**
     * 用户上传文件 objectKey（Yuxi 风格）：sessions/{sessionId}/inputs/{attachmentId}_{safeOriginalName}
     * <p>例：sessions/123/inputs/a1b2..._报告.pdf</p>
     */
    public static String inputObjectKey(Long sessionId, String attachmentId, String originalFileName) {
        return inputsPrefix(sessionId) + attachmentId + "_" + sanitizeFileName(originalFileName);
    }

    /** temp 区解析产物：chat/{agentId}/temp/parsed/{attachmentId}_{stem}.md */
    public static String tempParsedObjectKey(Long agentId, String attachmentId, String originalFileName) {
        return "chat/" + agentId + "/temp/parsed/" + attachmentId + "_"
                + stemFromFileName(originalFileName) + ".md";
    }

    /** 从 temp 区 objectKey 提取 agentId（chat/{agentId}/temp/...） */
    public static Long extractAgentIdFromTempObjectKey(String objectKey) {
        if (objectKey == null || !objectKey.startsWith("chat/")) {
            return null;
        }
        int secondSlash = objectKey.indexOf('/', 5);
        if (secondSlash <= 5) {
            return null;
        }
        try {
            return Long.parseLong(objectKey.substring(5, secondSlash));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 从 sessions/{sessionId}/... objectKey 提取 sessionId */
    public static Long extractSessionIdFromObjectKey(String objectKey) {
        if (objectKey == null || !objectKey.startsWith(SESSIONS_PREFIX)) {
            return null;
        }
        int end = objectKey.indexOf('/', SESSIONS_PREFIX.length());
        if (end <= SESSIONS_PREFIX.length()) {
            return null;
        }
        try {
            return Long.parseLong(objectKey.substring(SESSIONS_PREFIX.length(), end));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 文档解析产物 objectKey（Yuxi 风格）：sessions/{sessionId}/inputs/parsed/{stem}.md */
    public static String inputParsedObjectKey(Long sessionId, String originalFileName) {
        return inputsParsedPrefix(sessionId) + stemFromFileName(originalFileName) + ".md";
    }

    /**
     * 规范化用户原始文件名为 MinIO 安全片段（仅 basename，去除路径分隔符）。
     */
    public static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "attachment.bin";
        }
        String name = fileName.replace("\\", "/");
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replace("/", "_").replace("\\", "_");
        while (!name.isEmpty() && (name.charAt(0) == '.' || name.charAt(0) == ' ')) {
            name = name.substring(1);
        }
        while (!name.isEmpty() && (name.charAt(name.length() - 1) == '.' || name.charAt(name.length() - 1) == ' ')) {
            name = name.substring(0, name.length() - 1);
        }
        return name.isEmpty() ? "attachment.bin" : name;
    }

    /** 从原始文件名提取不带扩展名的 stem（用于 parsed 产物命名） */
    public static String stemFromFileName(String fileName) {
        String safe = sanitizeFileName(fileName);
        String lower = safe.toLowerCase(Locale.ROOT);
        for (String ext : new String[]{".docx", ".txt", ".html", ".htm", ".pdf", ".md", ".xlsx", ".xls", ".pptx", ".ppt", ".csv"}) {
            if (lower.endsWith(ext)) {
                return safe.substring(0, safe.length() - ext.length()).replace("/", "_").replace("\\", "_");
            }
        }
        int dot = safe.lastIndexOf('.');
        return dot > 0 ? safe.substring(0, dot) : safe;
    }

    /** AI 生图 objectKey：sessions/{sessionId}/outputs/images/{fileId}.jpg */
    public static String outputImageObjectKey(Long sessionId, String fileId) {
        return outputsImagesPrefix(sessionId) + fileId + ".jpg";
    }

    /** AI 交付物 objectKey：sessions/{sessionId}/outputs/files/{sanitizedRelativePath} */
    public static String outputFileObjectKey(Long sessionId, String relativePath) {
        return outputsFilesPrefix(sessionId) + sanitizeRelativePath(relativePath);
    }

    /** 沙盒工作区 objectKey：sessions/{sessionId}/workspace/{relativePath} */
    public static String workspaceObjectKey(Long sessionId, String relativePath) {
        return workspacePrefix(sessionId) + sanitizeRelativePath(relativePath);
    }

    /**
     * 兼容旧调用：用户上传 objectKey。
     *
     * @deprecated 使用 {@link #inputObjectKey}
     */
    @Deprecated
    public static String uploadObjectKey(Long sessionId, String attachmentId, String originalFileName) {
        return inputObjectKey(sessionId, attachmentId, originalFileName);
    }

    /**
     * 兼容旧调用：沙盒工作区 objectKey（旧实现直接落在 session 根下，已迁移到 workspace/）。
     *
     * @deprecated 使用 {@link #workspaceObjectKey}
     */
    @Deprecated
    public static String sandboxObjectKey(Long sessionId, String relativePath) {
        return workspaceObjectKey(sessionId, relativePath);
    }

    public static boolean isSessionScoped(Long sessionId) {
        return sessionId != null && sessionId > 0;
    }

    /**
     * 将相对路径规范化为 MinIO objectKey 片段：去除前导斜杠、反斜杠转正斜杠、禁止路径穿越。
     */
    private static String sanitizeRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "";
        }
        String normalized = relativePath.replace("\\", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        // 防御路径穿越
        if (normalized.contains("..")) {
            throw new IllegalArgumentException("非法相对路径：禁止包含 ..");
        }
        return normalized;
    }
}

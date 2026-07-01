package com.lightbot.service.impl;

import com.lightbot.common.BizException;
import com.lightbot.dto.SessionAttachmentVO;
import com.lightbot.dto.SessionFileContentVO;
import com.lightbot.dto.SessionFileEntryVO;
import com.lightbot.dto.SessionFileStatsVO;
import com.lightbot.dto.SessionFileTreeResponseVO;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.SessionAttachmentSource;
import com.lightbot.service.ChatSessionService;
import com.lightbot.service.SessionFileService;
import com.lightbot.util.MinioUtil;
import com.lightbot.util.SessionStoragePath;
import io.minio.StatObjectResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 会话文件服务实现：基于 MinIO 非递归扫描构建懒加载目录树，索引 enrich 来源信息。
 *
 * @author finch
 * @since 2026-06-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionFileServiceImpl implements SessionFileService {

    private static final Set<String> TEXT_MIMES = Set.of(
            "text/plain", "text/markdown", "text/html", "text/csv", "application/json", "application/xml");
    private static final long MAX_TEXT_PREVIEW_BYTES = 512 * 1024L;

    private final MinioUtil minioUtil;
    private final ChatSessionService chatSessionService;

    @Override
    public SessionFileTreeResponseVO listDirectory(Long sessionId, String path) {
        SessionFileTreeResponseVO resp = new SessionFileTreeResponseVO();
        String normalized = normalizeRelativePath(path);
        String root = SessionStoragePath.sessionRoot(sessionId);

        // 1. 根路径返回三个固定顶级目录（与 Yuxi 一致，即使为空也展示）
        if (normalized.isEmpty()) {
            resp.setEntries(rootDirectoryEntries(sessionId));
            resp.setStats(computeStats(sessionId));
            return resp;
        }

        // 2. 校验路径必须落在允许的分区下
        ensureAllowedPartition(normalized);

        // 3. MinIO 非递归列举直接子条目
        String prefix = root + normalized + "/";
        List<MinioUtil.MinioDirEntry> rawEntries = safeList(prefix);
        // 兼容旧路径：uploads/ 同时并入 inputs/
        if (normalized.equals(SessionStoragePath.INPUTS_DIR)) {
            rawEntries.addAll(legacyUploadsEntries(sessionId));
        }

        // 4. 构建 attachments 索引 map（按 objectKey 索引）
        Map<String, SessionAttachmentVO> index = buildAttachmentIndex(sessionId);

        // 5. 转换 + enrich + 排序（目录优先，名称字典序），过滤 .keep 占位
        List<SessionFileEntryVO> entries = new ArrayList<>();
        for (MinioUtil.MinioDirEntry raw : rawEntries) {
            if (".keep".equals(raw.name)) {
                continue;
            }
            entries.add(toEntry(root, normalized, raw, index));
        }
        entries.sort(Comparator
                .comparing((SessionFileEntryVO e) -> !Boolean.TRUE.equals(e.getDirectory()))
                .thenComparing(e -> e.getName() == null ? "" : e.getName(), String.CASE_INSENSITIVE_ORDER));
        resp.setEntries(entries);
        resp.setStats(computeStats(sessionId));
        return resp;
    }

    @Override
    public SessionFileContentVO readContent(Long sessionId, String path) {
        String normalized = normalizeRelativePath(path);
        ensureAllowedPartition(normalized);
        String objectKey = SessionStoragePath.sessionRoot(sessionId) + normalized;

        SessionFileContentVO vo = new SessionFileContentVO();
        vo.setPath(normalized);
        vo.setObjectKey(objectKey);

        StatObjectResponse stat;
        try {
            stat = minioUtil.statObject(objectKey);
        } catch (Exception e) {
            vo.setSupported(false);
            vo.setPreviewType("unsupported");
            vo.setMessage("文件不存在或已被删除");
            return vo;
        }
        vo.setSize(stat.size());
        String mime = stat.contentType() != null ? stat.contentType() : guessMimeFromName(normalized);
        vo.setMimeType(mime);

        // 1. 文本/Markdown/JSON/CSV/HTML：直接读内容
        String previewType = detectPreviewType(mime, normalized);
        if ("text".equals(previewType) || "markdown".equals(previewType)) {
            if (stat.size() > MAX_TEXT_PREVIEW_BYTES) {
                vo.setSupported(true);
                vo.setPreviewType(previewType);
                vo.setMessage("文件过大，建议下载后查看");
                vo.setPreviewUrl(minioUtil.getPresignedUrl(objectKey, mime));
                return vo;
            }
            try (InputStream in = minioUtil.download(objectKey)) {
                String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                vo.setSupported(true);
                vo.setPreviewType(previewType);
                vo.setContent(content);
                return vo;
            } catch (Exception e) {
                vo.setSupported(false);
                vo.setPreviewType("unsupported");
                vo.setMessage("读取内容失败：" + e.getMessage());
                return vo;
            }
        }

        // 2. 图片/PDF/视频：返回预签名 URL
        if ("image".equals(previewType) || "pdf".equals(previewType) || "video".equals(previewType)) {
            vo.setSupported(true);
            vo.setPreviewType(previewType);
            vo.setPreviewUrl(minioUtil.getPresignedUrl(objectKey, mime));
            return vo;
        }

        // 3. 其他二进制：返回下载 URL
        vo.setSupported(true);
        vo.setPreviewType("download");
        vo.setPreviewUrl(minioUtil.getPresignedUrl(objectKey, mime));
        return vo;
    }

    @Override
    public String getDownloadUrl(Long sessionId, String path) {
        String normalized = normalizeRelativePath(path);
        ensureAllowedPartition(normalized);
        String objectKey = SessionStoragePath.sessionRoot(sessionId) + normalized;
        String mime = guessMimeFromName(normalized);
        // 下载用 attachment disposition
        return minioUtil.getPresignedUrl(objectKey, mime);
    }

    @Override
    public void deleteFile(Long sessionId, String path) {
        String normalized = normalizeRelativePath(path);
        ensureAllowedPartition(normalized);
        // 禁止删除顶级分区目录
        if (isTopLevelPartition(normalized)) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "不允许删除顶级目录");
        }
        String objectKey = SessionStoragePath.sessionRoot(sessionId) + normalized;
        minioUtil.delete(objectKey);
        // 同步移除 attachments 索引中匹配 objectKey 的记录
        try {
            chatSessionService.removeSessionAttachmentByObjectKey(sessionId, objectKey);
        } catch (Exception e) {
            log.warn("[SessionFile] 同步移除附件索引失败: sessionId={}, objectKey={}", sessionId, objectKey, e);
        }
    }

    // ==================== 内部工具 ====================

    private List<SessionFileEntryVO> rootDirectoryEntries(Long sessionId) {
        List<SessionFileEntryVO> entries = new ArrayList<>();
        entries.add(dirEntry(SessionStoragePath.INPUTS_DIR, "用户上传"));
        entries.add(dirEntry(SessionStoragePath.OUTPUTS_DIR, "AI 产出"));
        entries.add(dirEntry(SessionStoragePath.WORKSPACE_DIR, "Agent 工作区"));
        // 兼容历史 uploads/ 目录：若 MinIO 中存在则额外展示
        if (hasAnyObject(SessionStoragePath.sessionRoot(sessionId) + "uploads/")) {
            entries.add(dirEntry("uploads", "历史上传（兼容）"));
        }
        return entries;
    }

    private SessionFileEntryVO dirEntry(String name, String displayName) {
        SessionFileEntryVO e = new SessionFileEntryVO();
        e.setName(displayName);
        e.setPath(name);
        e.setDirectory(true);
        return e;
    }

    private List<MinioUtil.MinioDirEntry> safeList(String prefix) {
        try {
            return minioUtil.listDirectoryEntries(prefix);
        } catch (Exception e) {
            log.warn("[SessionFile] 列举目录失败: prefix={}, error={}", prefix, e.getMessage());
            return List.of();
        }
    }

    /** 兼容历史 sessions/{id}/uploads/ 下的文件，并入 inputs/ 视图 */
    private List<MinioUtil.MinioDirEntry> legacyUploadsEntries(Long sessionId) {
        try {
            return minioUtil.listDirectoryEntries(SessionStoragePath.sessionRoot(sessionId) + "uploads/");
        } catch (Exception e) {
            return List.of();
        }
    }

    private boolean hasAnyObject(String prefix) {
        try {
            return !minioUtil.listDirectoryEntries(prefix).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private SessionFileEntryVO toEntry(String root, String parentPath, MinioUtil.MinioDirEntry raw,
                                       Map<String, SessionAttachmentVO> index) {
        SessionFileEntryVO e = new SessionFileEntryVO();
        e.setPath(parentPath.isEmpty() ? raw.name : parentPath + "/" + raw.name);
        e.setDirectory(raw.directory);
        if (raw.directory) {
            e.setName(raw.name);
            return e;
        }
        e.setObjectKey(raw.objectName);
        e.setSize(raw.size);
        e.setModifiedAt(raw.lastModified);
        // 索引 enrich：source / fileName / mimeType / previewUrl
        SessionAttachmentVO att = index.get(raw.objectName);
        String displayName = resolveDisplayName(raw.name, att);
        e.setName(displayName);
        if (att != null) {
            e.setSource(att.getSource());
            if (att.getFileName() != null) {
                e.setFileName(att.getFileName());
            } else {
                e.setFileName(displayName);
            }
            if (att.getMimeType() != null) {
                e.setMimeType(att.getMimeType());
            } else {
                e.setMimeType(guessMimeFromName(raw.name));
            }
            if (att.getPreviewUrl() != null) {
                e.setPreviewUrl(att.getPreviewUrl());
            }
        } else {
            e.setSource("unknown");
            e.setFileName(displayName);
            e.setMimeType(guessMimeFromName(raw.name));
        }
        return e;
    }

    /**
     * 优先使用 attachments 索引中的原名；否则从 Yuxi 风格 objectKey（{id}_{原名}）还原显示名。
     */
    private static String resolveDisplayName(String objectBaseName, SessionAttachmentVO att) {
        if (att != null && att.getFileName() != null && !att.getFileName().isBlank()) {
            return att.getFileName();
        }
        if (objectBaseName != null && objectBaseName.length() > 33 && objectBaseName.charAt(32) == '_') {
            String prefix = objectBaseName.substring(0, 32);
            if (prefix.matches("[0-9a-fA-F]{32}")) {
                return objectBaseName.substring(33);
            }
        }
        return objectBaseName;
    }

    private Map<String, SessionAttachmentVO> buildAttachmentIndex(Long sessionId) {
        List<SessionAttachmentVO> attachments = chatSessionService.getSessionAttachments(sessionId);
        Map<String, SessionAttachmentVO> map = new HashMap<>();
        if (attachments == null) {
            return map;
        }
        for (SessionAttachmentVO att : attachments) {
            if (att.getObjectKey() != null) {
                map.put(att.getObjectKey(), att);
            }
        }
        return map;
    }

    private SessionFileStatsVO computeStats(Long sessionId) {
        List<SessionAttachmentVO> attachments = chatSessionService.getSessionAttachments(sessionId);
        SessionFileStatsVO stats = new SessionFileStatsVO();
        if (attachments == null) {
            return stats;
        }
        int user = 0;
        int ai = 0;
        for (SessionAttachmentVO att : attachments) {
            String src = att.getSource();
            if (SessionAttachmentSource.USER_UPLOAD.getCode().equals(src)) {
                user++;
            } else if (src != null && src.startsWith("ai_")) {
                ai++;
            }
        }
        stats.setTotal(attachments.size());
        stats.setUserUpload(user);
        stats.setAiGenerated(ai);
        return stats;
    }

    private String normalizeRelativePath(String path) {
        if (path == null) {
            return "";
        }
        String p = path.trim().replace("\\", "/");
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        if (p.contains("..")) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "非法路径");
        }
        return p;
    }

    private void ensureAllowedPartition(String normalized) {
        if (normalized.isEmpty()) {
            return;
        }
        if (normalized.startsWith(SessionStoragePath.INPUTS_DIR + "/") || normalized.equals(SessionStoragePath.INPUTS_DIR)
                || normalized.startsWith(SessionStoragePath.OUTPUTS_DIR + "/") || normalized.equals(SessionStoragePath.OUTPUTS_DIR)
                || normalized.startsWith(SessionStoragePath.WORKSPACE_DIR + "/") || normalized.equals(SessionStoragePath.WORKSPACE_DIR)
                || normalized.startsWith("uploads/") || normalized.equals("uploads")) {
            return;
        }
        throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "路径不在允许的会话分区下");
    }

    private boolean isTopLevelPartition(String normalized) {
        return normalized.equals(SessionStoragePath.INPUTS_DIR)
                || normalized.equals(SessionStoragePath.OUTPUTS_DIR)
                || normalized.equals(SessionStoragePath.WORKSPACE_DIR)
                || normalized.equals("uploads");
    }

    private String detectPreviewType(String mime, String name) {
        if (mime == null) {
            mime = guessMimeFromName(name);
        }
        if (mime.startsWith("image/")) {
            return "image";
        }
        if (mime.startsWith("video/")) {
            return "video";
        }
        if ("application/pdf".equals(mime)) {
            return "pdf";
        }
        if ("text/markdown".equals(mime) || name.toLowerCase().endsWith(".md")) {
            return "markdown";
        }
        if (TEXT_MIMES.contains(mime)) {
            return "text";
        }
        return "unsupported";
    }

    private String guessMimeFromName(String name) {
        if (name == null) {
            return "application/octet-stream";
        }
        String lower = name.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".md")) return "text/markdown";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".xml")) return "application/xml";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".mov")) return "video/quicktime";
        return "application/octet-stream";
    }
}

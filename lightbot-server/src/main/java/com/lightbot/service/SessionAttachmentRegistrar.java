package com.lightbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.constant.ToolResultPrefixes;
import com.lightbot.dto.SessionAttachmentVO;
import com.lightbot.enums.SessionAttachmentSource;
import com.lightbot.util.SessionStoragePath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 工具执行成功后，将会话内产生的文件注册到 chat_session.attachments
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionAttachmentRegistrar {

    private final ChatSessionService chatSessionService;
    private final ObjectMapper objectMapper;

    /**
     * 根据工具名与返回 JSON 注册附件
     *
     * @param sessionId  会话 ID
     * @param toolName   工具名
     * @param toolResult 工具返回（JSON 或错误文本）
     */
    public void registerFromToolResult(Long sessionId, String toolName, String toolResult) {
        if (sessionId == null || toolName == null || toolResult == null || toolResult.isBlank()) {
            return;
        }
        if (ToolResultPrefixes.isError(toolResult)) {
            return;
        }
        try {
            List<SessionAttachmentVO> toRegister = switch (toolName) {
                case "image_generation" -> parseImageGeneration(sessionId, toolResult);
                case "sandbox_write_file" -> parseSandboxWrite(sessionId, toolResult);
                case "present_artifacts" -> parsePresentArtifacts(sessionId, toolResult);
                default -> List.of();
            };
            if (!toRegister.isEmpty()) {
                chatSessionService.registerSessionAttachments(sessionId, toRegister);
            }
        } catch (Exception e) {
            log.warn("[SessionAttachment] 注册失败: sessionId={}, tool={}, error={}", sessionId, toolName, e.getMessage());
        }
    }

    private List<SessionAttachmentVO> parseImageGeneration(Long sessionId, String toolResult) throws Exception {
        JsonNode root = objectMapper.readTree(toolResult);
        String filePath = root.path("file_path").asText(null);
        if (filePath == null || filePath.isBlank()) {
            return List.of();
        }
        SessionAttachmentVO vo = baseVo(sessionId, SessionAttachmentSource.AI_IMAGE.getCode());
        vo.setObjectKey(filePath);
        vo.setFileName(extractFileName(filePath));
        vo.setType("image");
        vo.setMimeType("image/jpeg");
        if (root.has("image_url")) {
            vo.setPreviewUrl(root.get("image_url").asText());
        }
        vo.setToolName("image_generation");
        return List.of(vo);
    }

    private List<SessionAttachmentVO> parseSandboxWrite(Long sessionId, String toolResult) throws Exception {
        JsonNode root = objectMapper.readTree(toolResult);
        if (!root.path("success").asBoolean(false)) {
            return List.of();
        }
        String relativePath = root.path("path").asText(null);
        if (relativePath == null || relativePath.isBlank()) {
            return List.of();
        }
        // outputs/ 路径走 AI 交付物分区，其余走 workspace/
        SessionAttachmentVO vo;
        if (relativePath.startsWith("outputs/")) {
            vo = baseVo(sessionId, SessionAttachmentSource.AI_DELIVER.getCode());
            vo.setObjectKey(SessionStoragePath.sessionRoot(sessionId) + relativePath);
        } else {
            vo = baseVo(sessionId, SessionAttachmentSource.AI_SANDBOX.getCode());
            vo.setObjectKey(SessionStoragePath.workspaceObjectKey(sessionId, relativePath));
        }
        vo.setRelativePath(relativePath);
        vo.setFileName(extractFileName(relativePath));
        vo.setType(guessTypeFromName(relativePath));
        vo.setMimeType(guessMimeFromName(relativePath));
        vo.setToolName("sandbox_write_file");
        return List.of(vo);
    }

    private List<SessionAttachmentVO> parsePresentArtifacts(Long sessionId, String toolResult) throws Exception {
        JsonNode root = objectMapper.readTree(toolResult);
        if (!root.path("success").asBoolean(false)) {
            return List.of();
        }
        JsonNode artifacts = root.get("artifacts");
        if (artifacts == null || !artifacts.isArray()) {
            return List.of();
        }
        List<SessionAttachmentVO> list = new ArrayList<>();
        for (JsonNode item : artifacts) {
            String relativePath = item.path("path").asText(null);
            if (relativePath == null || relativePath.isBlank()) {
                continue;
            }
            SessionAttachmentVO vo = baseVo(sessionId, SessionAttachmentSource.AI_DELIVER.getCode());
            // present_artifacts 限定 outputs/ 路径；objectKey = sessions/{id}/{relativePath}
            String normalized = relativePath.startsWith("outputs/") ? relativePath : "outputs/" + relativePath;
            vo.setObjectKey(SessionStoragePath.sessionRoot(sessionId) + normalized);
            vo.setRelativePath(normalized);
            vo.setFileName(item.has("name") ? item.get("name").asText() : extractFileName(normalized));
            vo.setType(guessTypeFromName(vo.getFileName()));
            if (item.hasNonNull("contentType")) {
                vo.setMimeType(item.get("contentType").asText());
            } else {
                vo.setMimeType(guessMimeFromName(vo.getFileName()));
            }
            if (item.has("url")) {
                vo.setPreviewUrl(item.get("url").asText());
            }
            vo.setToolName("present_artifacts");
            list.add(vo);
        }
        return list;
    }

    private SessionAttachmentVO baseVo(Long sessionId, String source) {
        SessionAttachmentVO vo = new SessionAttachmentVO();
        vo.setId(UUID.randomUUID().toString().replace("-", ""));
        vo.setSource(source);
        vo.setCreatedAt(LocalDateTime.now().toString());
        return vo;
    }

    private static String extractFileName(String path) {
        if (path == null) {
            return "未命名文件";
        }
        int idx = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    private static String guessTypeFromName(String name) {
        if (name == null) {
            return "document";
        }
        String lower = name.toLowerCase();
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".webp")) {
            return "image";
        }
        if (lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mov")) {
            return "video";
        }
        return "document";
    }

    private static String guessMimeFromName(String name) {
        if (name == null) {
            return "application/octet-stream";
        }
        String lower = name.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".md")) {
            return "text/markdown";
        }
        if (lower.endsWith(".json")) {
            return "application/json";
        }
        if (lower.endsWith(".txt")) {
            return "text/plain";
        }
        return "application/octet-stream";
    }
}

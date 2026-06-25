package com.lightbot.service.impl;

import com.lightbot.common.BizException;
import com.lightbot.constant.ChatAttachmentConstants;
import com.lightbot.constant.ConfigKeys;
import com.lightbot.dto.AgentChatCapabilitiesDTO;
import com.lightbot.dto.ChatAttachmentDTO;
import com.lightbot.enums.ErrorCode;
import com.lightbot.service.AgentService;
import com.lightbot.service.ChatAttachmentService;
import com.lightbot.util.AgentChatCapabilitiesUtil;
import com.lightbot.util.ChatContentSecurityScanUtil;
import com.lightbot.util.MinioUtil;
import com.lightbot.util.TikaUtil;
import com.lightbot.workflow.WorkflowConfigParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 对话附件：校验 Agent 能力、格式与大小；图片/视频走多模态，文档走 Tika 解析
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAttachmentServiceImpl implements ChatAttachmentService {

    private final AgentService agentService;
    private final MinioUtil minioUtil;
    private final TikaUtil tikaUtil;
    private final ObjectMapper objectMapper;
    private final ChatContentSecurityScanUtil contentSecurityScanUtil;

    @Override
    public ChatAttachmentDTO upload(Long agentId, Long sessionId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "请选择文件");
        }
        var agent = agentService.getById(agentId);
        if (agent == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND);
        }
        Map<String, Object> config = WorkflowConfigParser.parseConfigMap(agent.getConfig(), objectMapper);
        AgentChatCapabilitiesDTO caps = AgentChatCapabilitiesUtil.fromConfigMap(config);
        if (!Boolean.TRUE.equals(caps.getAllowFileUpload())) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "当前 Agent 未开启附件上传");
        }

        String mime = resolveMime(file);
        String ext = ChatAttachmentConstants.normalizeExtension(file.getOriginalFilename());
        String type = resolveAttachmentType(mime, ext, caps);

        try {
            // 1. 先做大小校验（使用 file.getSize() 避免不必要的内存加载）
            validateFileSize(file, type, caps);

            // 2. 读取字节：Tika 解析 + MinIO 上传均需要完整内容
            byte[] bytes = file.getBytes();
            return switch (type) {
                case "document" -> uploadDocument(file, bytes, mime, ext, agentId, sessionId, caps, config);
                case "image", "video" -> storeAndBuildDto(file, bytes, mime, type, agentId, sessionId, null, false);
                default -> throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "不支持的附件类型");
            };
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[ChatAttachment] 上传失败: {}", e.getMessage());
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    private void validateFileSize(MultipartFile file, String type, AgentChatCapabilitiesDTO caps) {
        long fileSize = file.getSize();
        if ("image".equals(type)) {
            if (fileSize > ChatAttachmentConstants.MAX_IMAGE_BYTES) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "图片不能超过 " + caps.getMaxImageSizeLabel());
            }
        } else if ("video".equals(type)) {
            if (fileSize > ChatAttachmentConstants.MAX_VIDEO_BYTES) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "视频不能超过 " + caps.getMaxVideoSizeLabel());
            }
        } else if ("document".equals(type)) {
            if (fileSize > ChatAttachmentConstants.MAX_DOCUMENT_BYTES) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "文档不能超过 " + caps.getMaxDocumentSizeLabel());
            }
        }
    }

    private ChatAttachmentDTO uploadDocument(MultipartFile file, byte[] bytes, String mime, String ext,
                                             Long agentId, Long sessionId, AgentChatCapabilitiesDTO caps,
                                             Map<String, Object> configMap) {
        String extNoDot = ext.startsWith(".") ? ext.substring(1) : ext;
        if (!tikaUtil.isSupported(extNoDot)) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                    "不支持的文档格式，支持：MD/TXT/PDF/Word/PPT/Excel/CSV/HTML");
        }
        String parsed = tikaUtil.parse(new ByteArrayInputStream(bytes), file.getOriginalFilename());
        if (parsed == null || parsed.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "未能从文件中解析出文本，请换一份文件或检查是否加密/扫描件");
        }
        boolean[] truncated = new boolean[1];
        String text = ChatAttachmentConstants.truncateParsedText(parsed.trim(), truncated);

        // 内容安全扫描（prompt 注入 + 敏感词）
        if (Boolean.TRUE.equals(configMap.get(ConfigKeys.Agent.ENABLE_CONTENT_SECURITY_SCAN))) {
            ChatContentSecurityScanUtil.ScanResult scanResult = contentSecurityScanUtil.scan(text, configMap);
            if (!scanResult.safe()) {
                throw new BizException(ErrorCode.CHAT_FILE_CONTENT_SUSPICIOUS);
            }
        }

        return storeAndBuildDto(file, bytes, mime, "document", agentId, sessionId, text, truncated[0]);
    }

    private ChatAttachmentDTO storeAndBuildDto(MultipartFile file, byte[] bytes, String mime, String type,
                                                Long agentId, Long sessionId, String parsedText,
                                                boolean parsedTruncated) {
        String attachmentId = UUID.randomUUID().toString().replace("-", "");
        String sessionPart = sessionId != null ? String.valueOf(sessionId) : "temp";
        String objectKey = "chat/" + agentId + "/" + sessionPart + "/" + attachmentId + extensionFromMime(mime, type);

        try {
            minioUtil.upload(new ByteArrayInputStream(bytes), objectKey, bytes.length, mime);
        } catch (Exception e) {
            log.error("[ChatAttachment] MinIO 上传失败: {}", e.getMessage());
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        ChatAttachmentDTO dto = new ChatAttachmentDTO();
        dto.setId(attachmentId);
        dto.setType(type);
        dto.setMimeType(mime);
        dto.setObjectKey(objectKey);
        dto.setFileName(file.getOriginalFilename());
        if (parsedText != null) {
            dto.setParsedText(parsedText);
            dto.setParsedTextTruncated(parsedTruncated);
        }
        try {
            dto.setPreviewUrl(minioUtil.getPresignedUrl(objectKey, mime));
        } catch (Exception e) {
            log.warn("[ChatAttachment] 生成预览 URL 失败: {}", e.getMessage());
        }
        return dto;
    }

    @Override
    public java.util.List<ChatAttachmentDTO> refreshPreviewUrls(java.util.List<ChatAttachmentDTO> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return java.util.List.of();
        }
        java.util.List<ChatAttachmentDTO> result = new java.util.ArrayList<>();
        for (ChatAttachmentDTO att : attachments) {
            if (att == null || att.getObjectKey() == null || att.getObjectKey().isBlank()) {
                continue;
            }
            ChatAttachmentDTO copy = new ChatAttachmentDTO();
            copy.setId(att.getId());
            copy.setType(att.getType());
            copy.setMimeType(att.getMimeType());
            copy.setObjectKey(att.getObjectKey());
            copy.setFileName(att.getFileName());
            copy.setParsedText(att.getParsedText());
            copy.setParsedTextTruncated(att.getParsedTextTruncated());
            try {
                String mime = att.getMimeType() != null ? att.getMimeType() : "application/octet-stream";
                copy.setPreviewUrl(minioUtil.getPresignedUrl(att.getObjectKey(), mime));
            } catch (Exception e) {
                log.warn("[ChatAttachment] 刷新预览 URL 失败: key={}, error={}", att.getObjectKey(), e.getMessage());
            }
            result.add(copy);
        }
        return result;
    }

    private String resolveMime(MultipartFile file) {
        String mime = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        if (ChatAttachmentConstants.IMAGE_MIMES.contains(mime)
                || ChatAttachmentConstants.VIDEO_MIMES.contains(mime)
                || ChatAttachmentConstants.DOCUMENT_MIMES.contains(mime)) {
            return mime;
        }
        String ext = ChatAttachmentConstants.normalizeExtension(file.getOriginalFilename());
        String fromExt = ChatAttachmentConstants.mimeFromExtension(ext);
        return fromExt != null ? fromExt : "application/octet-stream";
    }

    private String resolveAttachmentType(String mime, String ext, AgentChatCapabilitiesDTO caps) {
        Set<String> allowedMimes = caps.getAllowedFileMimeTypes() != null
                ? Set.copyOf(caps.getAllowedFileMimeTypes()) : Set.of();
        Set<String> allowedDocExt = caps.getAllowedDocumentExtensions() != null
                ? Set.copyOf(caps.getAllowedDocumentExtensions()) : Set.of();

        if (ChatAttachmentConstants.IMAGE_MIMES.contains(mime)) {
            if (!ChatAttachmentConstants.IMAGE_EXTENSIONS.contains(ext)) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                        "图片扩展名不支持，仅支持 JPG、PNG、WebP、GIF");
            }
            if (!Boolean.TRUE.equals(caps.getEnableImageInput()) || !allowedMimes.contains(mime)) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "当前 Agent 未开启图像输入");
            }
            return "image";
        }
        if (ChatAttachmentConstants.VIDEO_MIMES.contains(mime)) {
            if (!ChatAttachmentConstants.VIDEO_EXTENSIONS.contains(ext)) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                        "视频扩展名不支持，仅支持 MP4、WebM、MOV");
            }
            if (!Boolean.TRUE.equals(caps.getEnableVideoInput()) || !allowedMimes.contains(mime)) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "当前 Agent 未开启视频输入");
            }
            return "video";
        }
        if (ChatAttachmentConstants.DOCUMENT_EXTENSIONS.contains(ext)
                || ChatAttachmentConstants.DOCUMENT_MIMES.contains(mime)) {
            if (!Boolean.TRUE.equals(caps.getEnableFileRead())) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "当前 Agent 未开启文件读取");
            }
            if (!allowedDocExt.isEmpty() && !allowedDocExt.contains(ext)) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "当前 Agent 不允许该文档类型");
            }
            return "document";
        }
        throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                "不支持的文件类型。可上传：图片/视频（需开启多模态）或文档 MD/TXT/PDF/Office（需开启文件读取）");
    }

    private String extensionFromMime(String mime, String type) {
        if ("document".equals(type)) {
            return switch (mime) {
                case "text/plain" -> ".txt";
                case "text/markdown" -> ".md";
                case "application/pdf" -> ".pdf";
                case "application/msword" -> ".doc";
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx";
                case "application/vnd.ms-powerpoint" -> ".ppt";
                case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> ".pptx";
                case "application/vnd.ms-excel" -> ".xls";
                case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> ".xlsx";
                case "text/csv" -> ".csv";
                case "text/html" -> ".html";
                default -> ".bin";
            };
        }
        return switch (mime) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "video/mp4" -> ".mp4";
            case "video/webm" -> ".webm";
            case "video/quicktime" -> ".mov";
            default -> ".jpg";
        };
    }
}

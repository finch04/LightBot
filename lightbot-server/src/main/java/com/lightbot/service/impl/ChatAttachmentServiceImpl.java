package com.lightbot.service.impl;

import com.lightbot.common.BizException;
import com.lightbot.constant.ChatAttachmentConstants;
import com.lightbot.dto.AgentChatCapabilitiesDTO;
import com.lightbot.dto.ChatAttachmentDTO;
import com.lightbot.enums.ErrorCode;
import com.lightbot.service.AgentService;
import com.lightbot.service.ChatAttachmentService;
import com.lightbot.util.AgentChatCapabilitiesUtil;
import com.lightbot.util.MinioUtil;
import com.lightbot.workflow.WorkflowConfigParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 对话附件：校验 Agent 多模态配置、格式与大小后上传 MinIO
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAttachmentServiceImpl implements ChatAttachmentService {

    private final AgentService agentService;
    private final MinioUtil minioUtil;
    private final ObjectMapper objectMapper;

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
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "当前 Agent 未开启多模态文件上传");
        }

        String mime = resolveMime(file);
        String ext = ChatAttachmentConstants.normalizeExtension(file.getOriginalFilename());
        String type = resolveAttachmentType(mime, ext, caps);
        long maxSize = "image".equals(type) ? ChatAttachmentConstants.MAX_IMAGE_BYTES : ChatAttachmentConstants.MAX_VIDEO_BYTES;
        if (file.getSize() > maxSize) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                    "image".equals(type) ? "图片不能超过 4MB" : "视频不能超过 20MB");
        }

        String attachmentId = UUID.randomUUID().toString().replace("-", "");
        String sessionPart = sessionId != null ? String.valueOf(sessionId) : "temp";
        String objectKey = "chat/" + agentId + "/" + sessionPart + "/" + attachmentId + extensionFromMime(mime);

        try {
            minioUtil.upload(file.getInputStream(), objectKey, file.getSize(), mime);
        } catch (Exception e) {
            log.error("[ChatAttachment] 上传失败: {}", e.getMessage());
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        ChatAttachmentDTO dto = new ChatAttachmentDTO();
        dto.setId(attachmentId);
        dto.setType(type);
        dto.setMimeType(mime);
        dto.setObjectKey(objectKey);
        dto.setFileName(file.getOriginalFilename());
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
                || ChatAttachmentConstants.VIDEO_MIMES.contains(mime)) {
            return mime;
        }
        String ext = ChatAttachmentConstants.normalizeExtension(file.getOriginalFilename());
        String fromExt = ChatAttachmentConstants.mimeFromExtension(ext);
        return fromExt != null ? fromExt : "application/octet-stream";
    }

    private String resolveAttachmentType(String mime, String ext, AgentChatCapabilitiesDTO caps) {
        Set<String> allowedMimes = caps.getAllowedFileMimeTypes() != null
                ? Set.copyOf(caps.getAllowedFileMimeTypes()) : Set.of();

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
        throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                "不支持的文件类型，仅支持图片（JPG/PNG/WebP/GIF ≤4MB）或视频（MP4/WebM/MOV ≤20MB）");
    }

    private String extensionFromMime(String mime) {
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

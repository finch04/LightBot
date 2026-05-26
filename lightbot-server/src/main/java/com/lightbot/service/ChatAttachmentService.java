package com.lightbot.service;

import com.lightbot.dto.ChatAttachmentDTO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 对话附件上传服务
 */
public interface ChatAttachmentService {

    /**
     * 上传对话附件到 MinIO
     *
     * @param agentId   Agent ID
     * @param sessionId 会话 ID（可为空，新会话用临时路径）
     * @param file      文件
     * @return 附件信息
     */
    ChatAttachmentDTO upload(Long agentId, Long sessionId, MultipartFile file);
}

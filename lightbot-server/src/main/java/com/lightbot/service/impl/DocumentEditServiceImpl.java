package com.lightbot.service.impl;

import com.lightbot.common.BizException;
import com.lightbot.dto.DocumentEditRequest;
import com.lightbot.dto.DocumentEditSaveVO;
import com.lightbot.dto.EditableContentVO;
import com.lightbot.entity.Document;
import com.lightbot.enums.DocumentStatus;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.KnowledgeRole;
import com.lightbot.service.DocumentEditService;
import com.lightbot.service.DocumentService;
import com.lightbot.service.DocumentVersionService;
import com.lightbot.service.KnowledgeMemberService;
import com.lightbot.util.MinioUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * 文档在线编辑服务实现
 *
 * @author finch
 * @since 2026-06-16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentEditServiceImpl implements DocumentEditService {

    /** 已适配在线编辑的文件类型 */
    private static final Map<String, String> EDIT_MODE_MAP = Map.of(
            "md", "editor",
            "txt", "editor",
            "csv", "editor"
    );

    private static final Set<String> EDITABLE_TYPES = EDIT_MODE_MAP.keySet();

    private final DocumentService documentService;
    private final DocumentVersionService documentVersionService;
    private final MinioUtil minioUtil;
    private final KnowledgeMemberService permissionHelper;

    @Override
    public EditableContentVO getEditableContent(Long documentId) {
        Document doc = documentService.getById(documentId);
        if (doc == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND);
        }

        // 权限校验：需要成员权限
        permissionHelper.checkMember(doc.getKnowledgeId());

        String fileType = doc.getFileType() != null ? doc.getFileType().toLowerCase() : "";
        String editMode = EDIT_MODE_MAP.getOrDefault(fileType, "unsupported");
        boolean editable = EDITABLE_TYPES.contains(fileType);

        // MD/TXT/CSV：直接读取 Markdown 文件或原文件内容
        String content = "";
        if (editable) {
            content = readEditableContent(doc);
        }

        // 查询分块数量
        int totalChunks = doc.getChunkCount() != null ? doc.getChunkCount() : 0;

        EditableContentVO vo = new EditableContentVO();
        vo.setDocumentId(doc.getId());
        vo.setFileName(doc.getName());
        vo.setFileType(fileType);
        vo.setEditMode(editMode);
        vo.setContent(content);
        vo.setFileHash(doc.getFileHash());
        vo.setEditable(editable);
        vo.setTotalChars(content != null ? content.length() : 0);
        vo.setTotalChunks(totalChunks);

        return vo;
    }

    @Override
    public DocumentEditSaveVO saveContent(Long documentId, DocumentEditRequest request) {
        Document doc = documentService.getById(documentId);
        if (doc == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND);
        }

        // 权限校验：需要DEVELOPER及以上权限
        permissionHelper.checkPermission(doc.getKnowledgeId(), KnowledgeRole.DEVELOPER);

        // 1. 校验文档状态（只有已完成的文档才允许编辑）
        if (doc.getStatus() != DocumentStatus.COMPLETED && doc.getStatus() != DocumentStatus.UPLOADED) {
            throw new BizException(ErrorCode.DOCUMENT_INVALID_STATUS);
        }

        // 2. 乐观锁校验
        if (request.getExpectedHash() != null && !request.getExpectedHash().isBlank()) {
            if (!request.getExpectedHash().equals(doc.getFileHash())) {
                throw new BizException(ErrorCode.DOCUMENT_EDIT_CONFLICT);
            }
        }

        // 3. 校验编辑模式
        String fileType = doc.getFileType() != null ? doc.getFileType().toLowerCase() : "";
        String expectedEditMode = EDIT_MODE_MAP.get(fileType);
        if (expectedEditMode == null || !expectedEditMode.equals(request.getEditMode())) {
            throw new BizException(ErrorCode.DOCUMENT_EDIT_UNSUPPORTED);
        }

        // 4. 保存当前版本快照（覆盖前备份）
        String currentContent = readEditableContent(doc);
        int currentVersion = doc.getVersion() != null ? doc.getVersion() : 1;
        documentVersionService.saveVersion(documentId, currentVersion, doc.getFileHash(), currentContent);

        // 5. MD/TXT/CSV：直接覆盖 Markdown 文件
        String newContent = request.getContent();
        if (doc.getMarkdownPath() != null && !doc.getMarkdownPath().isBlank()) {
            // 优先覆盖已有的 Markdown 文件
            minioUtil.uploadString(newContent, doc.getMarkdownPath(), "text/plain");
        } else {
            // 没有 Markdown 文件时，覆盖原始文件
            String filePath = doc.getFilePath();
            try (InputStream is = new ByteArrayInputStream(newContent.getBytes(StandardCharsets.UTF_8))) {
                minioUtil.upload(is, filePath, newContent.getBytes(StandardCharsets.UTF_8).length, "text/plain");
            } catch (Exception e) {
                log.error("[文档编辑] MinIO上传失败, documentId={}", documentId, e);
                throw new BizException(ErrorCode.FILE_UPLOAD_FAILED);
            }
        }

        // 6. 更新文件哈希、版本号、编辑时间
        String newHash = calculateContentHash(newContent);
        doc.setFileHash(newHash);
        doc.setVersion(doc.getVersion() != null ? doc.getVersion() + 1 : 2);
        doc.setLastEditTime(LocalDateTime.now());
        documentService.updateById(doc);

        // 7. 异步触发全量重建（复用现有 ingestDocument 流程）
        try {
            documentService.ingestDocument(documentId, doc.getEmbeddingJson());
        } catch (Exception e) {
            log.warn("[文档编辑] 触发重建失败（文档已保存）, documentId={}: {}", documentId, e.getMessage());
        }

        log.info("[文档编辑] 保存成功, documentId={}, newVersion={}", documentId, doc.getVersion());

        DocumentEditSaveVO vo = new DocumentEditSaveVO();
        vo.setDocumentId(documentId);
        vo.setNewHash(newHash);
        vo.setMessage("保存成功，正在重新处理文档...");
        return vo;
    }

    /**
     * 读取可编辑内容：优先读 Markdown 文件，回退到实时转换
     */
    private String readEditableContent(Document doc) {
        // 优先读取已解析的 Markdown
        if (doc.getMarkdownPath() != null && !doc.getMarkdownPath().isBlank()) {
            try (InputStream is = minioUtil.download(doc.getMarkdownPath())) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.warn("[文档编辑] 读取Markdown文件失败, documentId={}, 回退到原文件", doc.getId(), e);
            }
        }

        // MD/TXT/CSV 直接读取原文件
        try (InputStream is = minioUtil.download(doc.getFilePath())) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[文档编辑] 读取文件失败, documentId={}", doc.getId(), e);
            throw new BizException(ErrorCode.DOCUMENT_READ_FAILED);
        }
    }

    /**
     * 计算文本内容的 MD5 哈希
     */
    private String calculateContentHash(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(System.currentTimeMillis());
        }
    }
}

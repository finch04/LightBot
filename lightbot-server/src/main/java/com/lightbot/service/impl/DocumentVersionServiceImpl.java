package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.dto.DocumentVersionVO;
import com.lightbot.entity.Document;
import com.lightbot.entity.DocumentVersion;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.KnowledgeRole;
import com.lightbot.mapper.DocumentVersionMapper;
import com.lightbot.service.DocumentService;
import com.lightbot.service.DocumentVersionService;
import com.lightbot.service.KnowledgePermissionHelper;
import com.lightbot.util.MinioUtil;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 文档版本服务实现
 *
 * @author finch
 * @since 2026-06-17
 */
@Slf4j
@Service
public class DocumentVersionServiceImpl extends ServiceImpl<DocumentVersionMapper, DocumentVersion>
        implements DocumentVersionService {

    @Autowired
    private MinioUtil minioUtil;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private KnowledgePermissionHelper permissionHelper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveVersion(Long documentId, Integer version, String contentHash, String content) {
        // 1. 存储内容到 MinIO
        String storagePath = "doc-versions/" + documentId + "/v" + version + ".txt";
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            minioUtil.upload(is, storagePath, bytes.length, "text/plain");
        } catch (Exception e) {
            log.error("[文档版本] 存储版本内容失败: documentId={}, version={}", documentId, version, e);
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        // 2. 插入版本记录
        DocumentVersion record = new DocumentVersion();
        record.setDocumentId(documentId);
        record.setVersion(version);
        record.setContentHash(contentHash);
        record.setStoragePath(storagePath);
        record.setCreatedBy(StpUtil.getLoginIdAsLong());
        save(record);
        log.info("[文档版本] 保存版本快照: documentId={}, version={}", documentId, version);
    }

    @Override
    public List<DocumentVersionVO> listVersions(Long documentId) {
        Document doc = documentService.getById(documentId);
        if (doc == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        permissionHelper.checkMember(doc.getKnowledgeId());

        List<DocumentVersion> versions = list(new LambdaQueryWrapper<DocumentVersion>()
                .eq(DocumentVersion::getDocumentId, documentId)
                .orderByDesc(DocumentVersion::getVersion));

        return versions.stream().map(v -> {
            DocumentVersionVO vo = new DocumentVersionVO();
            vo.setId(v.getId().toString());
            vo.setVersion(v.getVersion());
            vo.setContentHash(v.getContentHash());
            vo.setCreateTime(v.getCreateTime());
            return vo;
        }).toList();
    }

    @Override
    public String getVersionContent(Long versionId) {
        DocumentVersion version = getById(versionId);
        if (version == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        // 权限校验
        Document doc = documentService.getById(version.getDocumentId());
        if (doc == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        permissionHelper.checkMember(doc.getKnowledgeId());

        try (InputStream is = minioUtil.download(version.getStoragePath())) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[文档版本] 读取版本内容失败: versionId={}", versionId, e);
            throw new BizException(ErrorCode.FILE_DOWNLOAD_FAILED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollback(Long documentId, Long versionId) {
        // 1. 校验版本记录
        DocumentVersion version = getById(versionId);
        if (version == null || !version.getDocumentId().equals(documentId)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        // 2. 校验文档 + 权限
        Document doc = documentService.getById(documentId);
        if (doc == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        permissionHelper.checkPermission(doc.getKnowledgeId(), KnowledgeRole.DEVELOPER);

        // 3. 读取版本内容
        String content;
        try (InputStream is = minioUtil.download(version.getStoragePath())) {
            content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[文档版本] 读取版本内容失败: versionId={}", versionId, e);
            throw new BizException(ErrorCode.FILE_DOWNLOAD_FAILED);
        }

        // 4. 保存当前版本为快照（回滚前先备份）
        int nextVersion = doc.getVersion() != null ? doc.getVersion() + 1 : 2;
        saveVersion(documentId, doc.getVersion(), doc.getFileHash(), readCurrentContent(doc));

        // 5. 覆盖当前文档内容
        String targetPath = (doc.getMarkdownPath() != null && !doc.getMarkdownPath().isBlank())
                ? doc.getMarkdownPath() : doc.getFilePath();
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            minioUtil.upload(is, targetPath, bytes.length, "text/plain");
        } catch (Exception e) {
            log.error("[文档版本] 回滚写入MinIO失败: documentId={}", documentId, e);
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        // 6. 更新文档元数据
        String newHash = calculateContentHash(content);
        doc.setFileHash(newHash);
        doc.setVersion(nextVersion);
        doc.setLastEditTime(java.time.LocalDateTime.now());
        documentService.updateById(doc);

        // 7. 触发重建
        try {
            documentService.ingestDocument(documentId, doc.getEmbeddingJson());
        } catch (Exception e) {
            log.warn("[文档版本] 回滚后触发重建失败（文档已回滚）: documentId={}: {}", documentId, e.getMessage());
        }

        log.info("[文档版本] 回滚成功: documentId={}, rollbackToVersion={}, newVersion={}",
                documentId, version.getVersion(), nextVersion);
    }

    /**
     * 读取当前文档内容（用于回滚前备份）
     */
    private String readCurrentContent(Document doc) {
        String path = (doc.getMarkdownPath() != null && !doc.getMarkdownPath().isBlank())
                ? doc.getMarkdownPath() : doc.getFilePath();
        try (InputStream is = minioUtil.download(path)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[文档版本] 读取当前文档内容失败，跳过备份: documentId={}", doc.getId());
            return "";
        }
    }

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

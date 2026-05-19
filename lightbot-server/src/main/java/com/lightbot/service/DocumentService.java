package com.lightbot.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.entity.Document;
import com.lightbot.enums.DocumentStatus;
import com.lightbot.mapper.DocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.util.stream.Collectors;

/**
 * 文档服务
 *
 * @author finch
 * @since 2026-05-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService extends ServiceImpl<DocumentMapper, Document> {

    private final MinioService minioService;
    private final ChunkService chunkService;
    private final KnowledgeService knowledgeService;

    /**
     * 上传文档并处理
     *
     * @param knowledgeId 知识库ID
     * @param file        上传的文件
     * @return 文档记录
     */
    public Document uploadDocument(Long knowledgeId, MultipartFile file) {
        long userId = StpUtil.getLoginIdAsLong();
        String fileName = file.getOriginalFilename();

        // 1. 校验文件类型
        String fileType = extractFileType(fileName);
        if (!"md".equals(fileType)) {
            throw new BizException("目前仅支持 Markdown 文件");
        }

        // 2. 计算文件哈希
        String fileHash = calculateHash(file);

        // 3. 检查是否已上传过
        Document existing = getOne(new LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeId, knowledgeId)
                .eq(Document::getFileHash, fileHash)
                .eq(Document::getDeleted, 0));
        if (existing != null) {
            throw new BizException("该文件已上传过");
        }

        // 4. 上传到 MinIO
        String filePath = minioService.generatePath(knowledgeId, fileName);
        minioService.upload(file, filePath);

        // 5. 创建文档记录
        Document doc = new Document();
        doc.setKnowledgeId(knowledgeId);
        doc.setUserId(userId);
        doc.setName(fileName);
        doc.setFilePath(filePath);
        doc.setFileType(fileType);
        doc.setFileSize(file.getSize());
        doc.setFileHash(fileHash);
        doc.setStatus(DocumentStatus.PENDING);
        save(doc);

        // 6. 异步处理文档（切片 + 向量化）
        processDocumentAsync(doc.getId());

        return doc;
    }

    /**
     * 异步处理文档：读取内容 -> 分块 -> 存储
     */
    @Async
    public void processDocumentAsync(Long documentId) {
        Document doc = getById(documentId);
        if (doc == null) {
            return;
        }

        try {
            // 1. 更新状态为处理中
            doc.setStatus(DocumentStatus.PROCESSING);
            updateById(doc);

            // 2. 从 MinIO 读取文件内容
            InputStream is = minioService.download(doc.getFilePath());
            String content = new BufferedReader(new InputStreamReader(is))
                    .lines().collect(Collectors.joining("\n"));

            // 3. Markdown 分块
            int chunkSize = 512;
            int chunkOverlap = 50;
            var chunks = chunkService.splitMarkdown(content, chunkSize, chunkOverlap);

            // 4. 保存分块
            int totalTokens = 0;
            for (int i = 0; i < chunks.size(); i++) {
                String chunkContent = chunks.get(i);
                chunkService.saveChunk(doc.getId(), doc.getKnowledgeId(), i, chunkContent);
                totalTokens += estimateTokens(chunkContent);
            }

            // 5. 更新文档状态
            doc.setStatus(DocumentStatus.COMPLETED);
            doc.setChunkCount(chunks.size());
            doc.setTokenCount((long) totalTokens);
            updateById(doc);

            // 6. 更新知识库统计
            knowledgeService.updateStats(doc.getKnowledgeId(), 1, chunks.size(), totalTokens);

            log.info("[文档处理] 完成, documentId={}, chunks={}", documentId, chunks.size());

        } catch (Exception e) {
            log.error("[文档处理] 失败, documentId={}", documentId, e);
            doc.setStatus(DocumentStatus.FAILED);
            doc.setErrorMessage(e.getMessage());
            updateById(doc);
        }
    }

    /**
     * 获取文档预览内容
     */
    public String previewDocument(Long documentId) {
        Document doc = getById(documentId);
        if (doc == null) {
            throw new BizException("文档不存在");
        }
        try {
            InputStream is = minioService.download(doc.getFilePath());
            return new BufferedReader(new InputStreamReader(is))
                    .lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            throw new BizException("读取文档内容失败");
        }
    }

    private String extractFileType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "unknown";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    private String calculateHash(MultipartFile file) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(file.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(System.currentTimeMillis());
        }
    }

    private int estimateTokens(String text) {
        // 粗略估算：中文 1字≈1.5token，英文 1词≈1token
        return (int) (text.length() * 1.2);
    }
}

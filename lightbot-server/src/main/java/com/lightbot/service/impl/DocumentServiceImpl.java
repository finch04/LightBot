package com.lightbot.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.entity.Chunk;
import com.lightbot.entity.Document;
import com.lightbot.enums.ChunkStatus;
import com.lightbot.enums.DocumentStatus;
import com.lightbot.enums.ErrorCode;
import com.lightbot.mapper.DocumentMapper;
import com.lightbot.model.chunking.ChunkParams;
import com.lightbot.model.chunking.ChunkStrategy;
import com.lightbot.model.chunking.ChunkStrategyFactory;
import com.lightbot.service.*;
import com.lightbot.util.MinioUtil;
import com.lightbot.util.TikaUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档服务实现类
 *
 * @author finch
 * @since 2026-05-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, Document>
        implements DocumentService {

    private final MinioUtil minioUtil;
    private final TikaUtil tikaUtil;
    private final ChunkService chunkService;
    private final ChunkStrategyFactory chunkStrategyFactory;
    private final EmbeddingService embeddingService;
    private final EmbeddingModel embeddingModel;
    /** 延迟获取，避免与 KnowledgeServiceImpl 构造器循环依赖 */
    private final ObjectProvider<KnowledgeService> knowledgeServiceProvider;

    @Override
    public Document uploadDocument(Long knowledgeId, MultipartFile file, String chunkStrategy) {
        long userId = StpUtil.getLoginIdAsLong();
        String fileName = file.getOriginalFilename();

        // 1. 校验文件类型
        String fileType = extractFileType(fileName);
        if (!tikaUtil.isSupported(fileType)) {
            throw new BizException(ErrorCode.DOCUMENT_UNSUPPORTED_TYPE);
        }

        // 2. 计算文件MD5哈希，用于去重
        String fileHash = calculateHash(file);

        // 3. 检查同一知识库下是否已上传过相同文件
        Document existing = getOne(new LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeId, knowledgeId)
                .eq(Document::getFileHash, fileHash)
                .eq(Document::getDeleted, 0));
        if (existing != null) {
            throw new BizException(ErrorCode.DOCUMENT_ALREADY_EXISTS);
        }

        // 4. 上传文件到MinIO
        String filePath = minioUtil.generatePath(knowledgeId, fileName);
        minioUtil.upload(file, filePath);

        // 5. 创建文档记录
        Document doc = new Document();
        doc.setKnowledgeId(knowledgeId);
        doc.setUserId(userId);
        doc.setName(fileName);
        doc.setFilePath(filePath);
        doc.setFileType(fileType);
        doc.setFileSize(file.getSize());
        doc.setFileHash(fileHash);
        doc.setChunkStrategy(chunkStrategy);
        doc.setStatus(DocumentStatus.PENDING);
        save(doc);

        // 6. 异步处理文档（解析 -> 分块 -> 向量化）
        processDocumentAsync(doc.getId());

        return doc;
    }

    @Override
    public List<Document> uploadDocuments(Long knowledgeId, List<MultipartFile> files, String chunkStrategy) {
        List<Document> results = new ArrayList<>();
        for (MultipartFile file : files) {
            results.add(uploadDocument(knowledgeId, file, chunkStrategy));
        }
        return results;
    }

    @Override
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

            // 2. 从MinIO读取文件，使用Tika解析为纯文本
            InputStream is = minioUtil.download(doc.getFilePath());
            String content = tikaUtil.parse(is, doc.getName());

            // 3. 使用分块策略拆分
            ChunkStrategy strategy = chunkStrategyFactory.getStrategy(doc.getChunkStrategy());
            ChunkParams params = new ChunkParams();
            List<String> chunks = strategy.split(content, params);

            // 4. 保存分块到数据库，状态为 CHUNKED
            long totalTokens = 0;
            for (int i = 0; i < chunks.size(); i++) {
                String chunkContent = chunks.get(i);
                chunkService.saveChunk(doc.getId(), doc.getKnowledgeId(), i, chunkContent, ChunkStatus.CHUNKED);
                totalTokens += estimateTokens(chunkContent);
            }

            // 5. 更新文档状态和统计
            doc.setStatus(DocumentStatus.COMPLETED);
            doc.setChunkCount(chunks.size());
            doc.setTokenCount(totalTokens);
            updateById(doc);

            // 6. 更新知识库统计
            knowledgeServiceProvider.getObject().updateStats(doc.getKnowledgeId(), 1, chunks.size(), (int) totalTokens);

            log.info("[文档处理] 分块完成, documentId={}, chunks={}, strategy={}", documentId, chunks.size(), doc.getChunkStrategy());

            // 7. 异步向量化
            vectorizeChunks(doc.getId(), doc.getKnowledgeId());

        } catch (Exception e) {
            log.error("[文档处理] 失败, documentId={}", documentId, e);
            doc.setStatus(DocumentStatus.FAILED);
            doc.setErrorMessage(e.getMessage());
            updateById(doc);
        }
    }

    /**
     * 异步向量化：逐个将分块转为向量并存储
     *
     * @param documentId  文档ID
     * @param knowledgeId 知识库ID
     */
    @Async
    public void vectorizeChunks(Long documentId, Long knowledgeId) {
        // 1. 查询知识库的 Embedding 模型名称
        KnowledgeService knowledgeService = knowledgeServiceProvider.getObject();
        var knowledge = knowledgeService.getById(knowledgeId);
        if (knowledge == null || knowledge.getEmbeddingModel() == null) {
            log.warn("[向量化] 知识库未配置Embedding模型, knowledgeId={}", knowledgeId);
            return;
        }
        String modelName = knowledge.getEmbeddingModel();

        // 2. 查询该文档所有 CHUNKED 状态的分块
        List<Chunk> chunks = chunkService.list(new LambdaQueryWrapper<Chunk>()
                .eq(Chunk::getDocumentId, documentId)
                .eq(Chunk::getStatus, ChunkStatus.CHUNKED));

        log.info("[向量化] 开始, documentId={}, chunks={}", documentId, chunks.size());

        int success = 0;
        int failed = 0;

        for (Chunk chunk : chunks) {
            try {
                // 3. 更新状态为向量化中
                chunk.setStatus(ChunkStatus.VECTORIZING);
                chunkService.updateById(chunk);

                // 4. 调用 EmbeddingModel 将内容转为向量
                float[] vector = embedText(chunk.getContent());

                // 5. 存储向量
                embeddingService.saveVector(chunk.getId(), modelName, vector);

                // 6. 更新状态为已向量化
                chunk.setStatus(ChunkStatus.VECTORIZED);
                chunkService.updateById(chunk);
                success++;
            } catch (Exception e) {
                log.error("[向量化] 分块失败, chunkId={}", chunk.getId(), e);
                chunk.setStatus(ChunkStatus.FAILED);
                chunkService.updateById(chunk);
                failed++;
            }
        }

        log.info("[向量化] 完成, documentId={}, success={}, failed={}", documentId, success, failed);
    }

    /**
     * 文本向量化
     */
    private float[] embedText(String text) {
        EmbeddingResponse response = embeddingModel.call(
                new EmbeddingRequest(List.of(text), null));
        return response.getResult().getOutput();
    }

    @Override
    public String previewDocument(Long documentId) {
        Document doc = getById(documentId);
        if (doc == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        try {
            InputStream is = minioUtil.download(doc.getFilePath());
            return tikaUtil.parse(is, doc.getName());
        } catch (Exception e) {
            throw new BizException(ErrorCode.DOCUMENT_READ_FAILED);
        }
    }

    @Override
    public List<Document> listByKnowledgeId(Long knowledgeId) {
        return list(new LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeId, knowledgeId)
                .eq(Document::getDeleted, 0)
                .orderByDesc(Document::getCreateTime));
    }

    @Override
    public void deleteDocument(Long documentId) {
        Document doc = getById(documentId);
        if (doc == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        // 删除文档关联的向量
        embeddingService.deleteByDocumentId(documentId);
        removeById(documentId);
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
        return (int) (text.length() * 1.2);
    }
}

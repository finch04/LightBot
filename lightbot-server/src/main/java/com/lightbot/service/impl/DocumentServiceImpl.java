package com.lightbot.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.DocumentDownloadVO;
import com.lightbot.dto.DocumentStreamVO;
import com.lightbot.dto.DuplicateCheckResultVO;
import com.lightbot.dto.IngestRequest;
import com.lightbot.dto.UrlFetchPreviewVO;
import com.lightbot.dto.UrlSaveRequest;
import com.lightbot.entity.Chunk;
import com.lightbot.entity.Document;
import com.lightbot.entity.Knowledge;
import com.lightbot.enums.ChunkStatus;
import com.lightbot.enums.DocumentStatus;
import com.lightbot.enums.ErrorCode;
import com.lightbot.mapper.DocumentMapper;
import com.lightbot.model.chunking.ChunkParams;
import com.lightbot.model.chunking.ChunkStrategy;
import com.lightbot.model.chunking.ChunkStrategyFactory;
import com.lightbot.entity.Task;
import com.lightbot.enums.TaskType;
import com.lightbot.enums.KnowledgeRole;
import com.lightbot.service.*;
import com.lightbot.util.ContentDuplicateDetectionUtil;
import com.lightbot.util.DocumentSecurityScanUtil;
import com.lightbot.util.MinioUtil;
import com.lightbot.util.OcrUtil;
import com.lightbot.util.TextNormalizeUtil;
import com.lightbot.util.TikaUtil;
import com.lightbot.util.WebFetchUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

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

    /** 单文件大小上限 100MB */
    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024;

    /** 批量 Embedding 每批大小（受 Embedding API 限制，通常 50-200） */
    private static final int EMBED_BATCH_SIZE = 50;

    /** 并行入库线程池（控制并发，避免 Embedding API 限流） */
    private static final ExecutorService INGEST_EXECUTOR = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "doc-ingest");
        t.setDaemon(true);
        return t;
    });

    private final MinioUtil minioUtil;
    private final TikaUtil tikaUtil;
    private final OcrUtil ocrUtil;
    private final WebFetchUtil webFetchUtil;
    private final ChunkService chunkService;
    private final ChunkStrategyFactory chunkStrategyFactory;
    private final EmbeddingService embeddingService;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;
    private final TaskService taskService;
    /** 延迟获取，避免与 KnowledgeServiceImpl 构造器循环依赖 */
    private final ObjectProvider<KnowledgeService> knowledgeServiceProvider;
    private final ObjectProvider<GraphService> graphServiceProvider;
    private final DocumentSecurityScanUtil documentSecurityScanUtil;
    private final ContentDuplicateDetectionUtil contentDuplicateDetectionUtil;
    private final KnowledgeMemberService permissionHelper;

    @Override
    public Document uploadDocument(Long knowledgeId, MultipartFile file, boolean ocrEnabled) {
        // 权限校验：需要DEVELOPER及以上权限
        permissionHelper.checkPermission(knowledgeId, KnowledgeRole.DEVELOPER);

        long userId = StpUtil.getLoginIdAsLong();
        String fileName = file.getOriginalFilename();

        // 1. 校验文件类型
        String fileType = extractFileType(fileName);
        if (!tikaUtil.isSupported(fileType)) {
            throw new BizException(ErrorCode.DOCUMENT_UNSUPPORTED_TYPE);
        }

        // 2. 校验文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(ErrorCode.DOCUMENT_FILE_TOO_LARGE);
        }

        // 3. 计算文件MD5哈希，用于去重
        String fileHash = calculateHash(file);

        // 4. 检查同一知识库下是否已上传过相同文件
        Document existing = getOne(new LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeId, knowledgeId)
                .eq(Document::getFileHash, fileHash)
                .eq(Document::getDeleted, 0));
        if (existing != null) {
            throw new BizException(ErrorCode.DOCUMENT_ALREADY_EXISTS, existing.getName());
        }

        // 5. 保存文件到临时目录
        String tempFileName = UUID.randomUUID() + "." + fileType;
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"), "lightbot", "upload");
        Path tempFile = tempDir.resolve(tempFileName);
        try {
            Files.createDirectories(tempDir);
            file.transferTo(tempFile.toFile());
        } catch (IOException e) {
            log.error("[文档上传] 临时文件保存失败, fileName={}", fileName, e);
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        // 6. 生成 MinIO 存储路径（任务执行器会用此路径上传）
        String filePath = minioUtil.generatePath(knowledgeId, fileName);

        // 7. 创建文档记录（状态为 UPLOADING）
        Document doc = new Document();
        doc.setKnowledgeId(knowledgeId);
        doc.setUserId(userId);
        doc.setName(fileName);
        doc.setFilePath(filePath);
        doc.setFileType(fileType);
        doc.setFileSize(file.getSize());
        doc.setFileHash(fileHash);
        doc.setStatus(DocumentStatus.UPLOADING);
        save(doc);

        // 8. 创建上传任务并推入Redis队列
        String payload = String.format("{\"documentId\":%d,\"tempPath\":\"%s\",\"ocrEnabled\":%s}",
                doc.getId(), tempFile.toAbsolutePath().toString().replace("\\", "\\\\"), ocrEnabled);
        taskService.createTask(TaskType.DOCUMENT_UPLOAD, "文档上传 - " + fileName,
                userId, doc.getId(), payload);

        return doc;
    }

    @Override
    public List<Document> uploadDocuments(Long knowledgeId, List<MultipartFile> files, boolean ocrEnabled) {
        List<Document> results = new ArrayList<>();
        for (MultipartFile file : files) {
            results.add(uploadDocument(knowledgeId, file, ocrEnabled));
        }
        return results;
    }

    @Override
    public Task ingestDocument(Long documentId, String embeddingJson) {
        Document doc = getById(documentId);
        if (doc == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        // 权限校验：需要DEVELOPER及以上权限
        permissionHelper.checkPermission(doc.getKnowledgeId(), KnowledgeRole.DEVELOPER);
        if (doc.getStatus() != DocumentStatus.UPLOADED && doc.getStatus() != DocumentStatus.FAILED
                && doc.getStatus() != DocumentStatus.COMPLETED) {
            throw new BizException(ErrorCode.DOCUMENT_INVALID_STATUS);
        }

        // 1. 保存入库配置到文档
        doc.setEmbeddingJson(embeddingJson);
        doc.setStatus(DocumentStatus.PENDING);
        doc.setErrorMessage(null);
        updateById(doc);

        // 2. 创建任务并推入Redis队列
        String payload = String.format("{\"documentId\":%d,\"embeddingJson\":%s}", documentId, embeddingJson);
        return taskService.createTask(TaskType.DOCUMENT_INGEST, "文档入库 - " + doc.getName(),
                doc.getUserId(), documentId, payload);
    }

    @Override
    public List<String> previewChunks(Long documentId, String embeddingJson) {
        Document doc = getById(documentId);
        if (doc == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        // 权限校验：需要成员权限
        permissionHelper.checkMember(doc.getKnowledgeId());

        // 1. 优先使用上传阶段生成的解析文本（含OCR结果），否则解析原文件
        String content = null;
        if (doc.getMarkdownPath() != null && !doc.getMarkdownPath().isEmpty()) {
            try (InputStream mdIs = minioUtil.download(doc.getMarkdownPath())) {
                content = new String(mdIs.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.warn("[文档预览] 读取Markdown文件失败，回退到实时转换, documentId={}", documentId, e);
            }
        }
        if (content == null || content.isBlank()) {
            try (InputStream is = minioUtil.download(doc.getFilePath())) {
                content = tikaUtil.parse(is, doc.getName());
            } catch (Exception e) {
                log.warn("[文档预览] 解析原文件失败, documentId={}", documentId, e);
            }
        }

        // 2. 解析入库配置
        ChunkParams params = parseChunkParams(embeddingJson);
        String strategyName = parseChunkStrategy(embeddingJson);

        // 3. 分块
        ChunkStrategy strategy = chunkStrategyFactory.getStrategy(strategyName);
        return strategy.split(content, params);
    }

    @Override
    public void processDocumentWithProgress(Long documentId, String embeddingJson, BiConsumer<Integer, String> progressCallback)  {
        Document doc = getById(documentId);
        if (doc == null) {
            return;
        }

        try {
            // 0. 清理旧的分块和向量（重新入库时删除历史数据，首次入库时无数据可删）
            embeddingService.deleteByDocumentId(documentId);
            chunkService.remove(new LambdaQueryWrapper<Chunk>().eq(Chunk::getDocumentId, documentId));

            // 1. 优先使用上传阶段生成的解析文本（含OCR结果），否则重新解析原文件
            progressCallback.accept(5, "正在解析文档...");
            String content = null;
            if (doc.getMarkdownPath() != null && !doc.getMarkdownPath().isEmpty()) {
                try (InputStream mdIs = minioUtil.download(doc.getMarkdownPath())) {
                    content = new String(mdIs.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
            if (content == null || content.isBlank()) {
                try (InputStream is = minioUtil.download(doc.getFilePath())) {
                    content = tikaUtil.parse(is, doc.getName());
                }
            }

            // 1.1 解析失败（返回null）时标记失败
            if (content == null || content.isBlank()) {
                doc.setStatus(DocumentStatus.FAILED);
                doc.setErrorMessage("文档解析失败，可能是扫描版PDF、文件损坏或格式不支持");
                updateById(doc);
                return;
            }

            // 1.2 内容重复检测（入库时异步执行，结果存入文档记录）
            progressCallback.accept(10, "正在检测内容重复...");
            String dupWarning = detectContentDuplicate(doc, content);

            // 2. 解析入库配置，执行分块
            progressCallback.accept(20, "正在分块...");
            ChunkParams params = parseChunkParams(embeddingJson);
            String strategyName = parseChunkStrategy(embeddingJson);
            ChunkStrategy strategy = chunkStrategyFactory.getStrategy(strategyName);
            List<String> chunks = strategy.split(content, params);

            // 2.1 分块结果为空（所有分片低于最小token阈值）
            if (chunks.isEmpty()) {
                throw new BizException(ErrorCode.DOCUMENT_CHUNKS_TOO_SHORT);
            }

            // 3. 保存分块到数据库，状态为 CHUNKED
            progressCallback.accept(30, "正在保存分块...");
            long totalTokens = 0;
            for (int i = 0; i < chunks.size(); i++) {
                String chunkContent = TextNormalizeUtil.normalizeChunkContent(chunks.get(i));
                chunkService.saveChunk(doc.getId(), doc.getKnowledgeId(), i, chunkContent, ChunkStatus.CHUNKED);
                totalTokens += estimateTokens(chunkContent);
            }

            // 4. 更新文档状态为向量化中
            doc.setStatus(DocumentStatus.PROCESSING);
            doc.setChunkCount(chunks.size());
            doc.setTokenCount(totalTokens);
            updateById(doc);

            // 5. 更新知识库统计
            knowledgeServiceProvider.getObject().updateStats(doc.getKnowledgeId(), 1, chunks.size(), (int) totalTokens);
            log.info("[文档入库] 分块完成, documentId={}, chunks={}, strategy={}", documentId, chunks.size(), strategyName);

            // 6. 向量化
            vectorizeChunksWithProgress(doc.getId(), doc.getKnowledgeId(), (vectorProgress, msg) -> {
                // 向量化进度占 40%-95%
                int overallProgress = 40 + (int) (vectorProgress * 0.55);
                progressCallback.accept(overallProgress, msg);
            });

            String doneMsg = dupWarning != null ? "入库完成（" + dupWarning + "）" : "入库完成";
            progressCallback.accept(100, doneMsg);
        } catch (Exception e) {
            log.error("[文档入库] 失败, documentId={}", documentId, e);
            doc.setStatus(DocumentStatus.FAILED);
            doc.setErrorMessage(buildErrorMessage(e));
            updateById(doc);
            throw new BizException(ErrorCode.DOCUMENT_CHUNK_FAILED);
        }
    }

    public void vectorizeChunksWithProgress(Long documentId, Long knowledgeId, BiConsumer<Integer, String> progressCallback) {
        // 1. 查询知识库的 Embedding 模型名称
        KnowledgeService knowledgeService = knowledgeServiceProvider.getObject();
        Knowledge knowledge = knowledgeService.getById(knowledgeId);
        if (knowledge == null || knowledge.getEmbeddingModel() == null) {
            log.warn("[向量化] 知识库未配置Embedding模型, knowledgeId={}", knowledgeId);
            completeDocument(documentId);
            return;
        }
        String modelName = knowledge.getEmbeddingModel();

        // 2. 查询该文档所有 CHUNKED 状态的分块
        List<Chunk> chunks = chunkService.list(new LambdaQueryWrapper<Chunk>()
                .eq(Chunk::getDocumentId, documentId)
                .eq(Chunk::getStatus, ChunkStatus.CHUNKED));

        log.info("[向量化] 开始(批量模式), documentId={}, batchSize={}", documentId, chunks.size());

        int total = chunks.size();
        int success = 0;
        int failed = 0;

        // 3. 分批处理，每批 EMBED_BATCH_SIZE 个chunk
        for (int batchStart = 0; batchStart < total; batchStart += EMBED_BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + EMBED_BATCH_SIZE, total);
            List<Chunk> batch = chunks.subList(batchStart, batchEnd);

            // 3.1 批量调用 EmbeddingModel
            List<String> texts = batch.stream().map(Chunk::getContent).toList();
            float[][] vectors;
            try {
                vectors = embedBatch(texts);
            } catch (Exception e) {
                log.error("[向量化] 批量Embedding失败, batchStart={}", batchStart, e);
                // 批量失败时标记该批次所有chunk为失败
                for (Chunk chunk : batch) {
                    chunk.setStatus(ChunkStatus.FAILED);
                    chunkService.updateById(chunk);
                    failed++;
                }
                continue;
            }

            // 3.2 批量更新chunk状态为向量化中
            List<Long> chunkIds = batch.stream().map(Chunk::getId).toList();
            batch.forEach(c -> c.setStatus(ChunkStatus.VECTORIZING));
            chunkService.updateBatchById(batch);

            // 3.3 批量存储向量（传 knowledgeId 支持 Milvus 路由）
            ((EmbeddingServiceImpl) embeddingService).batchSaveVectors(knowledgeId, chunkIds, modelName, Arrays.asList(vectors));

            // 3.4 批量更新chunk状态为已向量化
            batch.forEach(c -> c.setStatus(ChunkStatus.VECTORIZED));
            chunkService.updateBatchById(batch);
            success += batch.size();

            // 3.5 报告进度
            int progress = (int) ((batchEnd * 1.0 / total) * 100);
            progressCallback.accept(progress, "向量化中 " + batchEnd + "/" + total);
        }

        log.info("[向量化] 完成, documentId={}, success={}, failed={}", documentId, success, failed);
        completeDocument(documentId);
    }

    /**
     * 向量化完成后更新文档状态为 COMPLETED，并触发示例问题生成、图谱抽取
     */
    private void completeDocument(Long documentId) {
        Document doc = getById(documentId);
        if (doc != null) {
            doc.setStatus(DocumentStatus.COMPLETED);
            doc.setErrorMessage(null);
            updateById(doc);

            Long knowledgeId = doc.getKnowledgeId();

            // 同步触发示例问题生成
            try {
                knowledgeServiceProvider.getObject().generateExampleQuestions(knowledgeId, documentId);
            } catch (Exception e) {
                log.warn("[文档入库] 示例问题生成失败, documentId={}", documentId, e);
            }

            // 异步触发图谱抽取（知识库开启 graphEnabled 时自动执行）
            try {
                Knowledge knowledge = knowledgeServiceProvider.getObject().getById(knowledgeId);
                if (knowledge != null && Boolean.TRUE.equals(knowledge.getGraphEnabled())) {
                    graphServiceProvider.getObject().autoExtractFromDocument(knowledgeId, documentId);
                    log.info("[文档入库] 自动触发图谱抽取, knowledgeId={}, documentId={}", knowledgeId, documentId);
                }
            } catch (Exception e) {
                log.warn("[文档入库] 自动图谱抽取失败, documentId={}", documentId, e);
            }
        }
    }

    private float[] embedText(String text) {
        EmbeddingResponse response = embeddingModel.call(
                new EmbeddingRequest(List.of(text), null));
        return response.getResult().getOutput();
    }

    /**
     * 批量文本向量化：一次请求处理多个文本，减少网络往返
     *
     * @param texts 文本列表
     * @return 向量列表，与输入文本一一对应
     */
    private float[][] embedBatch(List<String> texts) {
        EmbeddingResponse response = embeddingModel.call(
                new EmbeddingRequest(texts, null));
        return response.getResults().stream()
                .map(r -> r.getOutput())
                .toArray(float[][]::new);
    }

    @Override
    public String previewDocument(Long documentId) {
        Document doc = getById(documentId);
        if (doc == null) {
            return null;
        }
        // 权限校验：需要成员权限
        permissionHelper.checkMember(doc.getKnowledgeId());
        // 1. 优先读取已转换的Markdown文件
        if (doc.getMarkdownPath() != null) {
            try (InputStream is = minioUtil.download(doc.getMarkdownPath())) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.warn("[文档预览] 读取Markdown文件失败，回退到实时转换, documentId={}", documentId, e);
            }
        }
        // 2. 回退：实时转换
        try (InputStream is = minioUtil.download(doc.getFilePath())) {
            return tikaUtil.parseToMarkdown(is, doc.getName());
        } catch (Exception e) {
            log.warn("[文档预览] 解析失败, documentId={}", documentId, e);
            return null;
        }
    }

    @Override
    public String readDocumentContent(Long documentId) {
        Document doc = getById(documentId);
        if (doc == null) {
            return null;
        }
        // 1. 优先读取已转换的Markdown文件
        if (doc.getMarkdownPath() != null) {
            try (InputStream is = minioUtil.download(doc.getMarkdownPath())) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.warn("[文档读取] 读取Markdown文件失败，回退到实时转换, documentId={}", documentId, e);
            }
        }
        // 2. 回退：实时转换
        try (InputStream is = minioUtil.download(doc.getFilePath())) {
            return tikaUtil.parseToMarkdown(is, doc.getName());
        } catch (Exception e) {
            log.warn("[文档读取] 解析失败, documentId={}", documentId, e);
            return null;
        }
    }

    @Override
    public List<Document> listByKnowledgeId(Long knowledgeId) {
        // 权限校验：需要成员权限
        permissionHelper.checkMember(knowledgeId);
        return list(new LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeId, knowledgeId)
                .eq(Document::getDeleted, 0)
                .orderByDesc(Document::getCreateTime));
    }

    @Override
    public Page<Document> listByKnowledgeIdWithPage(Long knowledgeId, String keyword, int pageNum, int pageSize) {
        // 权限校验：需要成员权限
        permissionHelper.checkMember(knowledgeId);
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeId, knowledgeId)
                .eq(Document::getDeleted, 0);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Document::getName, keyword);
        }
        wrapper.orderByDesc(Document::getCreateTime);
        return baseMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public void deleteDocument(Long documentId) {
        Document doc = getById(documentId);
        if (doc == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        // 权限校验：需要DEVELOPER及以上权限
        permissionHelper.checkPermission(doc.getKnowledgeId(), KnowledgeRole.DEVELOPER);
        // 1. 删除MinIO中的文件
        try {
            minioUtil.delete(doc.getFilePath());
        } catch (Exception e) {
            log.warn("[文档删除] MinIO文件删除失败, filePath={}, error={}", doc.getFilePath(), e.getMessage());
        }
        // 2. 删除文档关联的向量
        embeddingService.deleteByDocumentId(documentId);
        // 3. 删除文档关联的分片
        chunkService.remove(new LambdaQueryWrapper<Chunk>().eq(Chunk::getDocumentId, documentId));
        // 4. 逻辑删除文档记录
        removeById(documentId);
        // 5. 递减知识库统计
        int chunkCount = doc.getChunkCount() != null ? doc.getChunkCount() : 0;
        long tokenCount = doc.getTokenCount() != null ? doc.getTokenCount() : 0;
        knowledgeServiceProvider.getObject().updateStats(doc.getKnowledgeId(), -1, -chunkCount, -tokenCount);
    }

    @Override
    public DocumentDownloadVO getDocumentDownloadUrl(Long documentId) {
        Document doc = getById(documentId);
        if (doc == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        // 权限校验：需要成员权限
        permissionHelper.checkMember(doc.getKnowledgeId());
        String contentType = getContentType(doc.getFileType());
        // 预签名URL中携带正确的Content-Type，浏览器才能内联展示而非下载
        String url = minioUtil.getPresignedUrl(doc.getFilePath(), contentType);
        return new DocumentDownloadVO(url, doc.getFileType(), doc.getName(), contentType);
    }

    @Override
    public DocumentStreamVO downloadDocumentAsStream(Long documentId) {
        Document doc = getById(documentId);
        if (doc == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        permissionHelper.checkMember(doc.getKnowledgeId());
        String contentType = getContentType(doc.getFileType());
        InputStream in = minioUtil.download(doc.getFilePath());
        return new DocumentStreamVO(in, doc.getName(), contentType);
    }

    @Override
    public UrlFetchPreviewVO previewUrlDocument(Long knowledgeId, String url) {
        // 权限校验：需要成员权限
        permissionHelper.checkMember(knowledgeId);
        WebFetchUtil.FetchResult result = webFetchUtil.fetch(url);
        return UrlFetchPreviewVO.builder()
                .url(result.getUrl())
                .title(result.getTitle())
                .content(result.getContent())
                .previewHtml(result.getPreviewHtml())
                .suggestedFileName(result.generateFileName())
                .contentLength(result.getContent().length())
                .description(result.getDescription())
                .build();
    }

    @Override
    public Document saveUrlDocument(Long knowledgeId, UrlSaveRequest request) {
        // 权限校验：需要DEVELOPER及以上权限
        permissionHelper.checkPermission(knowledgeId, KnowledgeRole.DEVELOPER);
        return persistUrlContent(knowledgeId, request.getUrl(), request.getTitle(), request.getContent(), System.currentTimeMillis());
    }

    @Override
    public Document fetchUrlDocument(Long knowledgeId, String url) {
        // 权限校验：需要DEVELOPER及以上权限
        permissionHelper.checkPermission(knowledgeId, KnowledgeRole.DEVELOPER);
        WebFetchUtil.FetchResult result = webFetchUtil.fetch(url);
        return persistUrlContent(knowledgeId, url, result.getTitle(), result.getContent(), result.getFetchedAt());
    }

    /**
     * 将 URL 正文持久化为知识库文档
     */
    private Document persistUrlContent(Long knowledgeId, String url, String title, String content, long fetchedAt) {
        long userId = StpUtil.getLoginIdAsLong();

        if (content == null || content.isBlank()) {
            throw new BizException(ErrorCode.DOCUMENT_PARSE_FAILED, "网页内容为空");
        }

        Knowledge knowledge = knowledgeServiceProvider.getObject().getById(knowledgeId);
        documentSecurityScanUtil.scanIfEnabled(knowledge, content);

        String fileName = buildUrlFileName(title, url);
        String contentHash = calculateContentHash(content);

        Document existing = getOne(new LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeId, knowledgeId)
                .eq(Document::getFileHash, contentHash)
                .eq(Document::getDeleted, 0));
        if (existing != null) {
            throw new BizException(ErrorCode.DOCUMENT_ALREADY_EXISTS, existing.getName());
        }

        String filePath = minioUtil.generatePath(knowledgeId, fileName);
        long contentSize = content.getBytes(StandardCharsets.UTF_8).length;
        try (InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
            minioUtil.upload(is, filePath, contentSize, "text/plain");
        } catch (Exception e) {
            log.error("[URL抓取] MinIO上传失败, url={}", url, e);
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        Document doc = new Document();
        doc.setKnowledgeId(knowledgeId);
        doc.setUserId(userId);
        doc.setName(fileName);
        doc.setFilePath(filePath);
        doc.setFileType("txt");
        doc.setFileSize(contentSize);
        doc.setFileHash(contentHash);
        doc.setStatus(DocumentStatus.UPLOADED);
        String safeTitle = title != null ? title.replace("\"", "\\\"") : "";
        doc.setMetadata(String.format("{\"sourceUrl\":\"%s\",\"title\":\"%s\",\"fetchedAt\":%d}",
                url, safeTitle, fetchedAt));
        save(doc);

        log.info("[URL抓取] 文档创建成功, documentId={}, url={}, contentLength={}", doc.getId(), url, content.length());
        return doc;
    }

    private String buildUrlFileName(String title, String url) {
        WebFetchUtil.FetchResult dummy = new WebFetchUtil.FetchResult(url, title, "", "", null);
        return dummy.generateFileName();
    }

    /**
     * 计算文本内容的哈希
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

    /**
     * 从 embeddingJson 解析分块参数
     */
    private ChunkParams parseChunkParams(String embeddingJson) {
        ChunkParams params = new ChunkParams();
        try {
            var node = objectMapper.readTree(embeddingJson);
            if (node.has("chunkSize")) {
                params.setChunkTokenNum(node.get("chunkSize").asInt(512));
            }
            if (node.has("chunkOverlap")) {
                params.setOverlappedPercent(node.get("chunkOverlap").asInt(10));
            }
            if (node.has("chunkDelimiter") && !node.get("chunkDelimiter").asText("").isBlank()) {
                params.setDelimiter(node.get("chunkDelimiter").asText("\n"));
            }
        } catch (Exception e) {
            log.warn("[DocumentService] 解析embeddingJson失败, 使用默认参数", e);
        }
        return params;
    }

    /**
     * 从 embeddingJson 解析分块策略名称
     */
    private String parseChunkStrategy(String embeddingJson) {
        try {
            var node = objectMapper.readTree(embeddingJson);
            if (node.has("chunkStrategy")) {
                return node.get("chunkStrategy").asText("general");
            }
        } catch (Exception e) {
            log.warn("[DocumentService] 解析chunkStrategy失败", e);
        }
        return "general";
    }

    private String extractFileType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "unknown";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * 生成Markdown文件存储路径（存放在知识库的parsed子目录）
     *
     * @param knowledgeId 知识库ID
     * @param filePath    原文件路径
     * @return Markdown文件路径
     */
    private String generateMarkdownPath(Long knowledgeId, String filePath) {
        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
        String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        return String.format("knowledge/%d/parsed/%s.md", knowledgeId, baseName);
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

    /**
     * 根据文件扩展名获取MIME类型
     */
    private String getContentType(String fileType) {
        if (fileType == null) {
            return "application/octet-stream";
        }
        return switch (fileType.toLowerCase()) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "csv" -> "text/csv";
            case "html", "htm" -> "text/html";
            case "md" -> "text/markdown";
            case "txt" -> "text/plain";
            default -> "application/octet-stream";
        };
    }

    /**
     * 构建错误消息，包含具体异常类型和原因
     */
    private String buildErrorMessage(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = e.getClass().getSimpleName();
        }
        // 截取堆栈前3行作为定位信息
        StackTraceElement[] stack = e.getStackTrace();
        if (stack.length > 0) {
            StringBuilder sb = new StringBuilder(msg);
            for (int i = 0; i < Math.min(3, stack.length); i++) {
                sb.append("\n  at ").append(stack[i].getClassName())
                        .append(".").append(stack[i].getMethodName())
                        .append(":").append(stack[i].getLineNumber());
            }
            return sb.toString();
        }
        return msg;
    }

    /**
     * 判断内容是否过短（可能是扫描件或图片文档）
     */
    private boolean isContentTooShort(String content, String fileType) {
        if (content == null || content.isBlank()) {
            return true;
        }
        // 对于PDF和图片类型，内容少于50个字符认为过短
        if ("pdf".equals(fileType) || "jpg".equals(fileType) || "jpeg".equals(fileType) || "png".equals(fileType)) {
            return content.trim().length() < 50;
        }
        return false;
    }

    /**
     * 尝试OCR识别
     */
    private String tryOcr(InputStream inputStream, String fileType) {
        try {
            if ("pdf".equals(fileType)) {
                return ocrUtil.recognizePdf(inputStream);
            } else if ("jpg".equals(fileType) || "jpeg".equals(fileType) || "png".equals(fileType)
                    || "bmp".equals(fileType) || "tiff".equals(fileType) || "tif".equals(fileType)) {
                return ocrUtil.recognizeImage(inputStream);
            }
        } catch (Exception e) {
            log.warn("[OCR] 识别失败, fileType={}", fileType, e);
        }
        return null;
    }

    /**
     * 合并OCR内容到Markdown
     */
    private String mergeOcrContent(String originalContent, String ocrContent) {
        if (originalContent == null || originalContent.isBlank()) {
            return ocrContent;
        }
        return originalContent + "\n\n---\n\n## OCR 识别内容\n\n" + ocrContent;
    }

    /**
     * 入库时内容重复检测：读取知识库配置，若开启则与已有文档比较相似度，结果存入 doc.duplicateRate
     *
     * @return 超过阈值时返回警告消息，未超过或未开启返回 null
     */
    private String detectContentDuplicate(Document doc, String content) {
        try {
            Knowledge knowledge = knowledgeServiceProvider.getObject().getById(doc.getKnowledgeId());
            if (knowledge == null || knowledge.getConfig() == null || knowledge.getConfig().isBlank()) {
                return null;
            }
            com.fasterxml.jackson.databind.JsonNode config = objectMapper.readTree(knowledge.getConfig());
            boolean enabled = config.has("duplicateDetectionEnabled") && config.get("duplicateDetectionEnabled").asBoolean(false);
            if (!enabled) {
                return null;
            }
            double threshold = config.has("duplicateThreshold") ? config.get("duplicateThreshold").asDouble(0.8) : 0.8;

            // 查询已有文档（排除当前文档，有解析内容即可，不限状态）
            List<Document> existingDocs = list(new LambdaQueryWrapper<Document>()
                    .eq(Document::getKnowledgeId, doc.getKnowledgeId())
                    .eq(Document::getDeleted, 0)
                    .ne(Document::getId, doc.getId())
                    .isNotNull(Document::getMarkdownPath)
                    .orderByDesc(Document::getCreateTime)
                    .last("LIMIT 50"));

            if (existingDocs.isEmpty()) {
                return null;
            }

            DuplicateCheckResultVO result = contentDuplicateDetectionUtil.checkDuplicate(content, existingDocs, threshold);
            doc.setDuplicateRate(result.getMaxSimilarity());
            log.info("[重复检测] documentId={}, maxSimilarity={}, threshold={}", doc.getId(), result.getMaxSimilarity(), threshold);

            // 存储top3相似文档详情（降序取前3）
            if (result.getDetails() != null && !result.getDetails().isEmpty()) {
                List<DuplicateCheckResultVO.DuplicateDetail> top3 = result.getDetails().stream()
                        .sorted((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()))
                        .limit(3)
                        .toList();
                doc.setDuplicateDetails(objectMapper.writeValueAsString(top3));
            }

            // 超过阈值返回警告消息
            if (result.getMaxSimilarity() >= threshold) {
                String percent = String.format("%.1f", result.getMaxSimilarity() * 100);
                String docName = result.getMostSimilarDocName() != null ? result.getMostSimilarDocName() : "未知";
                return String.format("内容重复率 %s%%（阈值 %.0f%%），与「%s」高度相似",
                        percent, threshold * 100, docName);
            }
            return null;
        } catch (Exception e) {
            // 重复检测失败不影响主入库流程
            log.warn("[重复检测] 执行失败, documentId={}", doc.getId(), e);
            return null;
        }
    }

}

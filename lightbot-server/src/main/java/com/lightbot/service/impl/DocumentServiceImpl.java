package com.lightbot.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.DocumentDownloadVO;
import com.lightbot.dto.IngestRequest;
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
import com.lightbot.service.*;
import com.lightbot.util.MinioUtil;
import com.lightbot.util.OcrUtil;
import com.lightbot.util.TikaUtil;
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
import java.util.List;
import java.util.UUID;
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

    private final MinioUtil minioUtil;
    private final TikaUtil tikaUtil;
    private final OcrUtil ocrUtil;
    private final ChunkService chunkService;
    private final ChunkStrategyFactory chunkStrategyFactory;
    private final EmbeddingService embeddingService;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;
    private final TaskService taskService;
    /** 延迟获取，避免与 KnowledgeServiceImpl 构造器循环依赖 */
    private final ObjectProvider<KnowledgeService> knowledgeServiceProvider;

    @Override
    public Document uploadDocument(Long knowledgeId, MultipartFile file, boolean ocrEnabled) {
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
        if (doc.getStatus() != DocumentStatus.UPLOADED && doc.getStatus() != DocumentStatus.FAILED) {
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

        // 1. 解析文件内容
        InputStream is = minioUtil.download(doc.getFilePath());
        String content = tikaUtil.parse(is, doc.getName());

        // 2. 解析入库配置
        ChunkParams params = parseChunkParams(embeddingJson);
        String strategyName = parseChunkStrategy(embeddingJson);

        // 3. 分块
        ChunkStrategy strategy = chunkStrategyFactory.getStrategy(strategyName);
        return strategy.split(content, params);
    }

    @Override
    public void processDocumentWithProgress(Long documentId, String embeddingJson, BiConsumer<Integer, String> progressCallback) {
        Document doc = getById(documentId);
        if (doc == null) {
            return;
        }

        try {
            // 1. 从MinIO读取文件，使用Tika解析为纯文本
            progressCallback.accept(5, "正在解析文档...");
            InputStream is = minioUtil.download(doc.getFilePath());
            String content = tikaUtil.parse(is, doc.getName());

            // 1.1 解析失败（返回null）时标记失败
            if (content == null) {
                doc.setStatus(DocumentStatus.FAILED);
                doc.setErrorMessage("文档解析失败，可能是扫描版PDF、文件损坏或格式不支持");
                updateById(doc);
                return;
            }

            // 2. 解析入库配置，执行分块
            progressCallback.accept(20, "正在分块...");
            ChunkParams params = parseChunkParams(embeddingJson);
            String strategyName = parseChunkStrategy(embeddingJson);
            ChunkStrategy strategy = chunkStrategyFactory.getStrategy(strategyName);
            List<String> chunks = strategy.split(content, params);

            // 3. 保存分块到数据库，状态为 CHUNKED
            progressCallback.accept(30, "正在保存分块...");
            long totalTokens = 0;
            for (int i = 0; i < chunks.size(); i++) {
                String chunkContent = chunks.get(i);
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

            progressCallback.accept(100, "入库完成");
        } catch (Exception e) {
            log.error("[文档入库] 失败, documentId={}", documentId, e);
            doc.setStatus(DocumentStatus.FAILED);
            doc.setErrorMessage(buildErrorMessage(e));
            updateById(doc);
            throw e;
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

        log.info("[向量化] 开始, documentId={}, chunks={}", documentId, chunks.size());

        int total = chunks.size();
        int success = 0;
        int failed = 0;

        for (int i = 0; i < total; i++) {
            Chunk chunk = chunks.get(i);
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

                // 7. 报告进度
                int progress = (int) ((i + 1.0) / total * 100);
                progressCallback.accept(progress, "向量化中 " + (i + 1) + "/" + total);
            } catch (Exception e) {
                log.error("[向量化] 分块失败, chunkId={}", chunk.getId(), e);
                chunk.setStatus(ChunkStatus.FAILED);
                chunkService.updateById(chunk);
                failed++;
            }
        }

        log.info("[向量化] 完成, documentId={}, success={}, failed={}", documentId, success, failed);
        completeDocument(documentId);
    }

    /**
     * 向量化完成后更新文档状态为 COMPLETED
     */
    private void completeDocument(Long documentId) {
        Document doc = getById(documentId);
        if (doc != null) {
            doc.setStatus(DocumentStatus.COMPLETED);
            updateById(doc);
        }
    }

    private float[] embedText(String text) {
        EmbeddingResponse response = embeddingModel.call(
                new EmbeddingRequest(List.of(text), null));
        return response.getResult().getOutput();
    }

    @Override
    public String previewDocument(Long documentId) {
        Document doc = getById(documentId);
        if (doc == null) {
            return null;
        }
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
        // 1. 删除MinIO中的文件
        try {
            minioUtil.delete(doc.getFilePath());
        } catch (Exception e) {
            log.warn("[文档删除] MinIO文件删除失败, filePath={}, error={}", doc.getFilePath(), e.getMessage());
        }
        // 2. 删除文档关联的向量
        embeddingService.deleteByDocumentId(documentId);
        // 3. 删除文档记录
        removeById(documentId);
    }

    @Override
    public DocumentDownloadVO getDocumentDownloadUrl(Long documentId) {
        Document doc = getById(documentId);
        if (doc == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        String contentType = getContentType(doc.getFileType());
        // 预签名URL中携带正确的Content-Type，浏览器才能内联展示而非下载
        String url = minioUtil.getPresignedUrl(doc.getFilePath(), contentType);
        return new DocumentDownloadVO(url, doc.getFileType(), doc.getName(), contentType);
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
            sb.append(" [at ");
            for (int i = 0; i < Math.min(3, stack.length); i++) {
                if (i > 0) sb.append(" <- ");
                sb.append(stack[i].getClassName()).append(".").append(stack[i].getMethodName());
                sb.append(":").append(stack[i].getLineNumber());
            }
            sb.append("]");
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
}

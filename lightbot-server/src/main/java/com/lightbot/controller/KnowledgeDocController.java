package com.lightbot.controller;

import com.lightbot.common.Result;
import com.lightbot.dto.ChunkVO;
import com.lightbot.dto.DocumentDownloadVO;
import com.lightbot.dto.DocumentStreamVO;
import com.lightbot.dto.IngestRequest;
import com.lightbot.dto.UrlFetchPreviewVO;
import com.lightbot.dto.UrlSaveRequest;
import com.lightbot.entity.Document;
import com.lightbot.entity.Task;
import com.lightbot.service.ChunkService;
import com.lightbot.service.DocumentService;
import com.lightbot.util.MinioUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * 知识库文档管理接口
 *
 * @author finch
 * @since 2026-06-21
 */
@Tag(name = "知识库文档管理", description = "文档上传、入库、预览、下载、分块查看")
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeDocController {

    private final DocumentService documentService;
    private final ChunkService chunkService;
    private final MinioUtil minioUtil;
    private final ObjectMapper objectMapper;

    // ========== 文档管理 ==========

    @Operation(summary = "上传文档到知识库（需要DEVELOPER及以上权限）")
    @PostMapping("/{id}/documents")
    public Result<Document> uploadDocument(@PathVariable Long id,
                                            @RequestParam("file") MultipartFile file,
                                            @RequestParam(defaultValue = "false") boolean ocrEnabled,
                                            @RequestParam(required = false) String force) {
        return Result.ok(documentService.uploadDocument(id, file, ocrEnabled, force));
    }

    @Operation(summary = "批量上传文档到知识库（需要DEVELOPER及以上权限）")
    @PostMapping("/{id}/documents/batch")
    public Result<List<Document>> uploadDocuments(@PathVariable Long id,
                                                   @RequestParam("files") List<MultipartFile> files,
                                                   @RequestParam(defaultValue = "false") boolean ocrEnabled,
                                                   @RequestParam(required = false) String force) {
        return Result.ok(documentService.uploadDocuments(id, files, ocrEnabled, force));
    }

    @Operation(summary = "预览URL网页内容（不入库）")
    @PostMapping("/{id}/documents/preview-url")
    public Result<UrlFetchPreviewVO> previewUrlDocument(@PathVariable Long id,
                                                         @RequestParam String url) {
        return Result.ok(documentService.previewUrlDocument(id, url));
    }

    @Operation(summary = "保存已预览的URL网页内容")
    @PostMapping("/{id}/documents/save-url")
    public Result<Document> saveUrlDocument(@PathVariable Long id,
                                             @Valid @RequestBody UrlSaveRequest request) {
        return Result.ok(documentService.saveUrlDocument(id, request));
    }

    @Operation(summary = "从URL抓取内容到知识库（需要DEVELOPER及以上权限）")
    @PostMapping("/{id}/documents/fetch-url")
    public Result<Document> fetchUrlDocument(@PathVariable Long id,
                                              @RequestParam String url) {
        return Result.ok(documentService.fetchUrlDocument(id, url));
    }

    @Operation(summary = "获取知识库下的文档列表（需要成员权限）")
    @GetMapping("/{id}/documents")
    public Result<?> listDocuments(@PathVariable Long id,
                                   @RequestParam(required = false) String keyword,
                                   @RequestParam(defaultValue = "1") int pageNum,
                                   @RequestParam(defaultValue = "50") int pageSize) {
        return Result.ok(documentService.listByKnowledgeIdWithPage(id, keyword, pageNum, pageSize));
    }

    @Operation(summary = "获取文档详情（需要成员权限）")
    @GetMapping("/documents/{docId}")
    public Result<Document> getDocument(@PathVariable Long docId) {
        return Result.ok(documentService.getById(docId));
    }

    @Operation(summary = "删除文档（需要DEVELOPER及以上权限）")
    @DeleteMapping("/documents/{docId}")
    public Result<Void> deleteDocument(@PathVariable Long docId) {
        documentService.deleteDocument(docId);
        return Result.ok();
    }

    @Operation(summary = "文档入库：分块+向量化（需要DEVELOPER及以上权限）")
    @PostMapping("/documents/{docId}/ingest")
    public Result<Task> ingestDocument(@PathVariable Long docId,
                                        @Valid @RequestBody IngestRequest request) throws Exception {
        String embeddingJson = objectMapper.writeValueAsString(request);
        return Result.ok(documentService.ingestDocument(docId, embeddingJson));
    }

    @Operation(summary = "手动同步 URL 文档（重新抓取，内容变更时更新并重新入库）")
    @PostMapping("/documents/{docId}/sync-url")
    public Result<Document> syncUrlDocument(@PathVariable Long docId) {
        return Result.ok(documentService.syncUrlDocument(docId));
    }

    @Operation(summary = "预览分块结果（不入库）")
    @PostMapping("/documents/{docId}/preview-chunks")
    public Result<List<String>> previewChunks(@PathVariable Long docId,
                                               @Valid @RequestBody IngestRequest request) throws Exception {
        String embeddingJson = objectMapper.writeValueAsString(request);
        return Result.ok(documentService.previewChunks(docId, embeddingJson));
    }

    @Operation(summary = "预览文档内容（需要成员权限）")
    @GetMapping("/documents/{docId}/preview")
    public Result<String> previewDocument(@PathVariable Long docId) {
        return Result.ok(documentService.previewDocument(docId));
    }

    @Operation(summary = "获取文档下载信息（预签名URL+文件类型）")
    @GetMapping("/documents/{docId}/download")
    public Result<DocumentDownloadVO> getDocumentDownloadUrl(@PathVariable Long docId) {
        return Result.ok(documentService.getDocumentDownloadUrl(docId));
    }

    @Operation(summary = "代理下载文档文件（强制下载，文件名正确）")
    @GetMapping("/documents/{docId}/download-file")
    public ResponseEntity<InputStreamResource> downloadFile(@PathVariable Long docId) {
        DocumentStreamVO stream = documentService.downloadDocumentAsStream(docId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(stream.getFileName(), java.nio.charset.StandardCharsets.UTF_8)
                .build();
        // InputStreamResource.close() 会自动关闭底层 InputStream，客户端断连时流会被释放
        InputStreamResource resource = new InputStreamResource(stream.getInputStream()) {
            @Override
            public String getFilename() {
                return stream.getFileName();
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stream.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }

    @Operation(summary = "代理获取知识库文档图片（供 Markdown 预览）")
    @GetMapping("/images/{knowledgeId}/{filename}")
    public ResponseEntity<InputStreamResource> getKnowledgeImage(
            @PathVariable Long knowledgeId, @PathVariable String filename) {
        String filePath = String.format("knowledge/%d/images/%s", knowledgeId, filename);
        try {
            var statObj = minioUtil.statObject(filePath);
            String contentType = statObj.contentType();
            InputStream is = minioUtil.downloadStream(filePath);
            // InputStreamResource.close() 会自动关闭底层 InputStream，客户端断连时 MinIO 流会被释放
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType != null ? contentType : "application/octet-stream"))
                    .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(7)))
                    .body(new InputStreamResource(is));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ========== 分块查看 ==========

    @Operation(summary = "获取文档的分块列表（含向量化状态）")
    @GetMapping("/documents/{docId}/chunks")
    public Result<List<ChunkVO>> listChunks(@PathVariable Long docId) {
        return Result.ok(chunkService.listChunkVOsByDocumentId(docId));
    }
}

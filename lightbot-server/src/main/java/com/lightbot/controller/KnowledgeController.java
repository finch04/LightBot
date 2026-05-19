package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.common.Result;
import com.lightbot.entity.Chunk;
import com.lightbot.entity.Document;
import com.lightbot.entity.Knowledge;
import com.lightbot.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lightbot.mapper.ChunkMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 知识库管理接口
 *
 * @author finch
 * @since 2026-05-19
 */
@Tag(name = "知识库管理", description = "知识库CRUD、文档上传、RAG问答")
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final DocumentService documentService;
    private final ChunkMapper chunkMapper;
    private final RagService ragService;

    // ========== 知识库 CRUD ==========

    @Operation(summary = "创建知识库")
    @PostMapping
    public Result<Knowledge> create(@RequestBody Knowledge knowledge) {
        return Result.ok(knowledgeService.create(knowledge));
    }

    @Operation(summary = "更新知识库")
    @PutMapping
    public Result<Knowledge> update(@RequestBody Knowledge knowledge) {
        return Result.ok(knowledgeService.update(knowledge));
    }

    @Operation(summary = "分页查询当前用户的知识库")
    @GetMapping
    public Result<Page<Knowledge>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(knowledgeService.listMyKnowledge(pageNum, pageSize));
    }

    @Operation(summary = "获取知识库详情")
    @GetMapping("/{id}")
    public Result<Knowledge> getById(@PathVariable Long id) {
        return Result.ok(knowledgeService.getById(id));
    }

    @Operation(summary = "删除知识库")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeService.deleteById(id);
        return Result.ok();
    }

    // ========== 文档管理 ==========

    @Operation(summary = "上传文档到知识库")
    @PostMapping("/{id}/documents")
    public Result<Document> uploadDocument(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return Result.ok(documentService.uploadDocument(id, file));
    }

    @Operation(summary = "获取知识库下的文档列表")
    @GetMapping("/{id}/documents")
    public Result<List<Document>> listDocuments(@PathVariable Long id) {
        List<Document> documents = documentService.list(
                new LambdaQueryWrapper<Document>()
                        .eq(Document::getKnowledgeId, id)
                        .eq(Document::getDeleted, 0)
                        .orderByDesc(Document::getCreateTime));
        return Result.ok(documents);
    }

    @Operation(summary = "获取文档详情")
    @GetMapping("/documents/{docId}")
    public Result<Document> getDocument(@PathVariable Long docId) {
        return Result.ok(documentService.getById(docId));
    }

    @Operation(summary = "删除文档")
    @DeleteMapping("/documents/{docId}")
    public Result<Void> deleteDocument(@PathVariable Long docId) {
        documentService.removeById(docId);
        return Result.ok();
    }

    @Operation(summary = "预览文档内容")
    @GetMapping("/documents/{docId}/preview")
    public Result<String> previewDocument(@PathVariable Long docId) {
        return Result.ok(documentService.previewDocument(docId));
    }

    // ========== 分块查看 ==========

    @Operation(summary = "获取文档的分块列表")
    @GetMapping("/documents/{docId}/chunks")
    public Result<List<Chunk>> listChunks(@PathVariable Long docId) {
        List<Chunk> chunks = chunkMapper.selectList(
                new LambdaQueryWrapper<Chunk>()
                        .eq(Chunk::getDocumentId, docId)
                        .orderByAsc(Chunk::getChunkIndex));
        return Result.ok(chunks);
    }

    // ========== RAG 问答 ==========

    @Operation(summary = "基于知识库RAG问答")
    @PostMapping("/{id}/ask")
    public Result<String> ask(@PathVariable Long id, @RequestParam String question) {
        return Result.ok(ragService.ask(id, question));
    }
}

package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.common.Result;
import com.lightbot.entity.Chunk;
import com.lightbot.entity.Document;
import com.lightbot.entity.Knowledge;
import com.lightbot.entity.KnowledgeMember;
import com.lightbot.enums.KnowledgeRole;
import com.lightbot.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库管理接口
 *
 * @author finch
 * @since 2026-05-19
 */
@Tag(name = "知识库管理", description = "知识库CRUD、成员管理、文档上传、RAG问答")
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final DocumentService documentService;
    private final ChunkService chunkService;
    private final RagService ragService;
    private final KnowledgeMemberService knowledgeMemberService;

    // ========== 知识库 CRUD ==========

    @Operation(summary = "创建知识库")
    @PostMapping
    public Result<Knowledge> create(@RequestBody Knowledge knowledge) {
        return Result.ok(knowledgeService.create(knowledge));
    }

    @Operation(summary = "更新知识库（需要MANAGER及以上权限）")
    @PutMapping
    public Result<Knowledge> update(@RequestBody Knowledge knowledge) {
        return Result.ok(knowledgeService.update(knowledge));
    }

    @Operation(summary = "分页查询当前用户有权限的知识库")
    @GetMapping
    public Result<Page<Knowledge>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(knowledgeService.listMyKnowledge(pageNum, pageSize));
    }

    @Operation(summary = "获取知识库详情（需要成员权限）")
    @GetMapping("/{id}")
    public Result<Knowledge> getById(@PathVariable Long id) {
        return Result.ok(knowledgeService.getByIdWithPermission(id));
    }

    @Operation(summary = "删除知识库（仅CREATOR）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeService.deleteById(id);
        return Result.ok();
    }

    // ========== 成员管理 ==========

    @Operation(summary = "添加成员（需要MANAGER及以上权限）")
    @PostMapping("/{id}/members")
    public Result<Void> addMember(@PathVariable Long id,
                                   @RequestParam Long userId,
                                   @RequestParam(defaultValue = "viewer") KnowledgeRole role) {
        knowledgeMemberService.addMember(id, userId, role);
        return Result.ok();
    }

    @Operation(summary = "更新成员角色（需要MANAGER及以上权限）")
    @PutMapping("/{id}/members/{userId}")
    public Result<Void> updateMemberRole(@PathVariable Long id,
                                          @PathVariable Long userId,
                                          @RequestParam KnowledgeRole role) {
        knowledgeMemberService.updateMemberRole(id, userId, role);
        return Result.ok();
    }

    @Operation(summary = "移除成员（需要MANAGER及以上权限）")
    @DeleteMapping("/{id}/members/{userId}")
    public Result<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        knowledgeMemberService.removeMember(id, userId);
        return Result.ok();
    }

    @Operation(summary = "获取知识库成员列表（需要成员权限）")
    @GetMapping("/{id}/members")
    public Result<List<KnowledgeMember>> listMembers(@PathVariable Long id) {
        return Result.ok(knowledgeMemberService.listMembers(id));
    }

    // ========== 文档管理 ==========

    @Operation(summary = "上传文档到知识库（需要DEVELOPER及以上权限）")
    @PostMapping("/{id}/documents")
    public Result<Document> uploadDocument(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return Result.ok(documentService.uploadDocument(id, file));
    }

    @Operation(summary = "获取知识库下的文档列表（需要成员权限）")
    @GetMapping("/{id}/documents")
    public Result<List<Document>> listDocuments(@PathVariable Long id) {
        return Result.ok(documentService.listByKnowledgeId(id));
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

    @Operation(summary = "预览文档内容（需要成员权限）")
    @GetMapping("/documents/{docId}/preview")
    public Result<String> previewDocument(@PathVariable Long docId) {
        return Result.ok(documentService.previewDocument(docId));
    }

    // ========== 分块查看 ==========

    @Operation(summary = "获取文档的分块列表（需要成员权限）")
    @GetMapping("/documents/{docId}/chunks")
    public Result<List<Chunk>> listChunks(@PathVariable Long docId) {
        return Result.ok(chunkService.listByDocumentId(docId));
    }

    // ========== 思维导图 ==========

    @Operation(summary = "生成知识库思维导图（AI总结）")
    @PostMapping("/{id}/mindmap")
    public Result<Object> generateMindmap(@PathVariable Long id,
                                          @RequestParam(required = false) Long providerId) {
        return Result.ok(knowledgeService.generateMindmap(id, providerId));
    }

    @Operation(summary = "获取知识库思维导图数据")
    @GetMapping("/{id}/mindmap")
    public Result<Object> getMindmap(@PathVariable Long id) {
        return Result.ok(knowledgeService.getMindmap(id));
    }

    // ========== RAG 问答 ==========

    @Operation(summary = "基于知识库RAG问答（需要VIEWER及以上权限）")
    @PostMapping("/{id}/ask")
    public Result<String> ask(@PathVariable Long id,
                              @RequestParam String question,
                              @RequestParam(required = false) Long providerId) {
        return Result.ok(ragService.ask(id, question, providerId));
    }
}

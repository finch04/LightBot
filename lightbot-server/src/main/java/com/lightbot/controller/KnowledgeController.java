package com.lightbot.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.common.BizException;
import com.lightbot.common.Result;
import com.lightbot.enums.ErrorCode;
import com.lightbot.entity.Chunk;
import com.lightbot.entity.Document;
import com.lightbot.entity.Knowledge;
import com.lightbot.entity.KnowledgeMember;
import com.lightbot.enums.KnowledgeRole;
import com.lightbot.mapper.ChunkMapper;
import com.lightbot.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库管理接口
 * <p>权限说明：
 * <ul>
 *   <li>CREATOR：完全权限，可删除知识库、管理所有成员</li>
 *   <li>MANAGER：可拉人、踢人、上传文档、对文档增删改查、提问</li>
 *   <li>DEVELOPER：可上传文档、对文档增删改查、提问</li>
 *   <li>VIEWER：只可提问、查看文档</li>
 * </ul>
 * </p>
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
    private final ChunkMapper chunkMapper;
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
        checkPermission(knowledge.getId(), KnowledgeRole.MANAGER);
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
        checkMember(id);
        return Result.ok(knowledgeService.getById(id));
    }

    @Operation(summary = "删除知识库（仅CREATOR）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        checkPermission(id, KnowledgeRole.CREATOR);
        knowledgeService.deleteById(id);
        return Result.ok();
    }

    // ========== 成员管理 ==========

    @Operation(summary = "添加成员（需要MANAGER及以上权限）")
    @PostMapping("/{id}/members")
    public Result<Void> addMember(@PathVariable Long id,
                                   @RequestParam Long userId,
                                   @RequestParam(defaultValue = "viewer") KnowledgeRole role) {
        checkPermission(id, KnowledgeRole.MANAGER);
        knowledgeMemberService.addMember(id, userId, role);
        return Result.ok();
    }

    @Operation(summary = "更新成员角色（需要MANAGER及以上权限）")
    @PutMapping("/{id}/members/{userId}")
    public Result<Void> updateMemberRole(@PathVariable Long id,
                                          @PathVariable Long userId,
                                          @RequestParam KnowledgeRole role) {
        checkPermission(id, KnowledgeRole.MANAGER);
        knowledgeMemberService.updateMemberRole(id, userId, role);
        return Result.ok();
    }

    @Operation(summary = "移除成员（需要MANAGER及以上权限）")
    @DeleteMapping("/{id}/members/{userId}")
    public Result<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        checkPermission(id, KnowledgeRole.MANAGER);
        knowledgeMemberService.removeMember(id, userId);
        return Result.ok();
    }

    @Operation(summary = "获取知识库成员列表（需要成员权限）")
    @GetMapping("/{id}/members")
    public Result<List<KnowledgeMember>> listMembers(@PathVariable Long id) {
        checkMember(id);
        return Result.ok(knowledgeMemberService.listMembers(id));
    }

    // ========== 文档管理 ==========

    @Operation(summary = "上传文档到知识库（需要DEVELOPER及以上权限）")
    @PostMapping("/{id}/documents")
    public Result<Document> uploadDocument(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        checkPermission(id, KnowledgeRole.DEVELOPER);
        return Result.ok(documentService.uploadDocument(id, file));
    }

    @Operation(summary = "获取知识库下的文档列表（需要成员权限）")
    @GetMapping("/{id}/documents")
    public Result<List<Document>> listDocuments(@PathVariable Long id) {
        checkMember(id);
        List<Document> documents = documentService.list(
                new LambdaQueryWrapper<Document>()
                        .eq(Document::getKnowledgeId, id)
                        .eq(Document::getDeleted, 0)
                        .orderByDesc(Document::getCreateTime));
        return Result.ok(documents);
    }

    @Operation(summary = "获取文档详情（需要成员权限）")
    @GetMapping("/documents/{docId}")
    public Result<Document> getDocument(@PathVariable Long docId) {
        Document doc = documentService.getById(docId);
        if (doc != null) {
            checkMember(doc.getKnowledgeId());
        }
        return Result.ok(doc);
    }

    @Operation(summary = "删除文档（需要DEVELOPER及以上权限）")
    @DeleteMapping("/documents/{docId}")
    public Result<Void> deleteDocument(@PathVariable Long docId) {
        Document doc = documentService.getById(docId);
        if (doc != null) {
            checkPermission(doc.getKnowledgeId(), KnowledgeRole.DEVELOPER);
        }
        documentService.removeById(docId);
        return Result.ok();
    }

    @Operation(summary = "预览文档内容（需要成员权限）")
    @GetMapping("/documents/{docId}/preview")
    public Result<String> previewDocument(@PathVariable Long docId) {
        Document doc = documentService.getById(docId);
        if (doc != null) {
            checkMember(doc.getKnowledgeId());
        }
        return Result.ok(documentService.previewDocument(docId));
    }

    // ========== 分块查看 ==========

    @Operation(summary = "获取文档的分块列表（需要成员权限）")
    @GetMapping("/documents/{docId}/chunks")
    public Result<List<Chunk>> listChunks(@PathVariable Long docId) {
        Document doc = documentService.getById(docId);
        if (doc != null) {
            checkMember(doc.getKnowledgeId());
        }
        List<Chunk> chunks = chunkMapper.selectList(
                new LambdaQueryWrapper<Chunk>()
                        .eq(Chunk::getDocumentId, docId)
                        .orderByAsc(Chunk::getChunkIndex));
        return Result.ok(chunks);
    }

    // ========== RAG 问答 ==========

    @Operation(summary = "基于知识库RAG问答（需要VIEWER及以上权限）")
    @PostMapping("/{id}/ask")
    public Result<String> ask(@PathVariable Long id, @RequestParam String question) {
        checkPermission(id, KnowledgeRole.VIEWER);
        return Result.ok(ragService.ask(id, question));
    }

    // ========== 权限校验 ==========

    /**
     * 校验当前用户是否为知识库成员
     */
    private void checkMember(Long knowledgeId) {
        long userId = StpUtil.getLoginIdAsLong();
        KnowledgeRole role = knowledgeMemberService.getMemberRole(knowledgeId, userId);
        if (role == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NO_PERMISSION);
        }
    }

    /**
     * 校验当前用户是否具有指定等级的角色
     */
    private void checkPermission(Long knowledgeId, KnowledgeRole requiredRole) {
        long userId = StpUtil.getLoginIdAsLong();
        if (!knowledgeMemberService.hasPermission(knowledgeId, userId, requiredRole)) {
            throw new BizException(ErrorCode.KNOWLEDGE_ROLE_INSUFFICIENT, requiredRole.getDesc());
        }
    }
}

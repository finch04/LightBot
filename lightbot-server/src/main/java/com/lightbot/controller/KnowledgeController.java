package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.common.Result;
import com.lightbot.dto.IngestRequest;
import com.lightbot.dto.KnowledgeMemberVO;
import com.lightbot.entity.Document;
import com.lightbot.entity.Knowledge;
import com.lightbot.enums.KnowledgeRole;
import com.lightbot.service.DocumentService;
import com.lightbot.service.KnowledgeMemberService;
import com.lightbot.service.KnowledgeService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 知识库管理接口（CRUD、成员管理、配置、思维导图、示例问题）
 *
 * @author finch
 * @since 2026-05-19
 */
@Slf4j
@Tag(name = "知识库管理", description = "知识库CRUD、成员管理、配置、思维导图、示例问题")
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final DocumentService documentService;
    private final KnowledgeMemberService knowledgeMemberService;
    @Qualifier("lightBotExecutor")
    private final Executor lightBotExecutor;

    // ========== 知识库 CRUD ==========

    @Operation(summary = "创建知识库")
    @PostMapping
    public Result<Knowledge> create(@Valid @RequestBody Knowledge knowledge) {
        return Result.ok(knowledgeService.create(knowledge));
    }

    @Operation(summary = "更新知识库（需要MANAGER及以上权限）")
    @PutMapping
    public Result<Knowledge> update(@Valid @RequestBody Knowledge knowledge) {
        return Result.ok(knowledgeService.update(knowledge));
    }

    @Operation(summary = "分页查询当前用户有权限的知识库")
    @GetMapping
    public Result<Page<Knowledge>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String name) {
        return Result.ok(knowledgeService.listMyKnowledge(pageNum, pageSize, name));
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
                                   @RequestParam(defaultValue = "viewer") String role) {
        knowledgeMemberService.addMember(id, userId, KnowledgeRole.fromValue(role));
        return Result.ok();
    }

    @Operation(summary = "更新成员角色（需要MANAGER及以上权限）")
    @PutMapping("/{id}/members/{userId}")
    public Result<Void> updateMemberRole(@PathVariable Long id,
                                          @PathVariable Long userId,
                                          @RequestParam String role) {
        knowledgeMemberService.updateMemberRole(id, userId, KnowledgeRole.fromValue(role));
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
    public Result<List<KnowledgeMemberVO>> listMembers(@PathVariable Long id) {
        return Result.ok(knowledgeMemberService.listMemberVOs(id));
    }

    // ========== 配置 ==========

    @Operation(summary = "获取知识库默认入库配置")
    @GetMapping("/{id}/default-ingest-config")
    public Result<IngestRequest> getDefaultIngestConfig(@PathVariable Long id) {
        return Result.ok(knowledgeService.getDefaultIngestConfig(id));
    }

    @Operation(summary = "获取知识库检索配置")
    @GetMapping("/{id}/query-params")
    public Result<Map<String, Object>> getQueryParams(@PathVariable Long id) {
        return Result.ok(knowledgeService.getQueryParams(id));
    }

    @Operation(summary = "更新知识库检索配置（需要MANAGER及以上权限）")
    @PutMapping("/{id}/query-params")
    public Result<Void> updateQueryParams(@PathVariable Long id,
                                           @RequestBody Map<String, Object> params) {
        knowledgeService.updateQueryParams(id, params);
        return Result.ok();
    }

    @Operation(summary = "检查 Milvus 连接状态")
    @GetMapping("/{id}/milvus-health")
    public Result<Map<String, Object>> milvusHealth(@PathVariable Long id) {
        return Result.ok(Map.of("available", knowledgeService.isMilvusAvailable()));
    }

    @Operation(summary = "全量重算知识库统计信息（需要成员权限）")
    @PostMapping("/{id}/stats/refresh")
    public Result<Void> refreshStats(@PathVariable Long id) {
        knowledgeService.refreshStats(id);
        return Result.ok();
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

    // ========== 示例问题 ==========

    @Operation(summary = "获取示例问题列表")
    @GetMapping("/{id}/example-questions")
    public Result<List<String>> getExampleQuestions(@PathVariable Long id) {
        return Result.ok(knowledgeService.getExampleQuestions(id));
    }

    @Operation(summary = "更新示例问题列表")
    @PutMapping("/{id}/example-questions")
    public Result<Void> updateExampleQuestions(@PathVariable Long id,
                                               @RequestBody List<String> questions) {
        knowledgeService.updateExampleQuestions(id, questions);
        return Result.ok();
    }

    @Operation(summary = "AI生成单个示例问题")
    @PostMapping("/{id}/example-questions/generate")
    public Result<String> generateOneExampleQuestion(@PathVariable Long id) {
        return Result.ok(knowledgeService.generateOneExampleQuestion(id));
    }

    @Operation(summary = "为知识库所有已完成文档生成示例问题")
    @PostMapping("/{id}/generate-questions")
    public Result<Void> generateQuestions(@PathVariable Long id) {
        // 异步执行，避免 LLM 逐文档调用阻塞 HTTP 线程
        lightBotExecutor.execute(() -> {
            try {
                List<Document> documents = documentService.listByKnowledgeId(id).stream()
                        .filter(doc -> doc.getStatus() == com.lightbot.enums.DocumentStatus.COMPLETED)
                        .toList();
                for (Document doc : documents) {
                    knowledgeService.generateExampleQuestions(id, doc.getId());
                }
            } catch (Exception e) {
                log.error("[Knowledge] 异步生成示例问题失败: knowledgeId={}", id, e);
            }
        });
        return Result.ok();
    }
}

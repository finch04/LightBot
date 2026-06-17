package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.common.Result;
import com.lightbot.dto.ChunkVO;
import com.lightbot.dto.DocumentDownloadVO;
import com.lightbot.dto.DocumentStreamVO;
import com.lightbot.dto.GraphEdgeVO;
import com.lightbot.dto.GraphExtractRequest;
import com.lightbot.dto.GraphImportRequest;
import com.lightbot.dto.GraphNodeVO;
import com.lightbot.dto.GraphStatsVO;
import com.lightbot.dto.GraphSubgraphVO;
import com.lightbot.dto.IngestRequest;
import com.lightbot.dto.KnowledgeMemberVO;
import com.lightbot.dto.QaPairCreateDTO;
import com.lightbot.dto.QaPairUpdateDTO;
import com.lightbot.dto.QaPairVO;
import com.lightbot.dto.RagSearchResultVO;
import com.lightbot.dto.UrlFetchPreviewVO;
import com.lightbot.dto.UrlSaveRequest;
import com.lightbot.entity.Document;
import com.lightbot.entity.Knowledge;
import com.lightbot.entity.Task;
import com.lightbot.enums.KnowledgeRole;
import com.lightbot.service.*;
import com.lightbot.util.MilvusUtil;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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
    private final GraphService graphService;
    private final QaPairService qaPairService;
    private final MilvusUtil milvusUtil;
    private final ObjectMapper objectMapper;

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
        return Result.ok(Map.of("available", milvusUtil.isAvailable()));
    }

    // ========== 文档管理 ==========

    @Operation(summary = "上传文档到知识库（需要DEVELOPER及以上权限）")
    @PostMapping("/{id}/documents")
    public Result<Document> uploadDocument(@PathVariable Long id,
                                            @RequestParam("file") MultipartFile file,
                                            @RequestParam(defaultValue = "false") boolean ocrEnabled) {
        return Result.ok(documentService.uploadDocument(id, file, ocrEnabled));
    }

    @Operation(summary = "批量上传文档到知识库（需要DEVELOPER及以上权限）")
    @PostMapping("/{id}/documents/batch")
    public Result<List<Document>> uploadDocuments(@PathVariable Long id,
                                                   @RequestParam("files") List<MultipartFile> files,
                                                   @RequestParam(defaultValue = "false") boolean ocrEnabled) {
        return Result.ok(documentService.uploadDocuments(id, files, ocrEnabled));
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
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stream.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new InputStreamResource(stream.getInputStream()));
    }

    // ========== 分块查看 ==========

    @Operation(summary = "获取文档的分块列表（含向量化状态）")
    @GetMapping("/documents/{docId}/chunks")
    public Result<List<ChunkVO>> listChunks(@PathVariable Long docId) {
        return Result.ok(chunkService.listChunkVOsByDocumentId(docId));
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
        // 遍历所有已完成文档，逐个生成问题
        List<Document> documents = documentService.listByKnowledgeId(id).stream()
                .filter(doc -> doc.getStatus() == com.lightbot.enums.DocumentStatus.COMPLETED)
                .toList();
        for (Document doc : documents) {
            knowledgeService.generateExampleQuestions(id, doc.getId());
        }
        return Result.ok();
    }

    // ========== 知识图谱 ==========

    @Operation(summary = "触发图谱抽取（需要DEVELOPER及以上权限）")
    @PostMapping("/{id}/graph/extract")
    public Result<Long> extractGraph(@PathVariable Long id,
                                     @Valid @RequestBody GraphExtractRequest request) {
        return Result.ok(graphService.extractFromDocument(id, request));
    }

    @Operation(summary = "批量导入三元组（需要DEVELOPER及以上权限）")
    @PostMapping("/{id}/graph/import")
    public Result<GraphStatsVO> importTriples(@PathVariable Long id,
                                              @Valid @RequestBody GraphImportRequest request,
                                              @RequestParam(required = false) Long providerId) {
        return Result.ok(graphService.importTriples(id, request.getTriples(), providerId));
    }

    @Operation(summary = "获取子图数据（可视化用）")
    @GetMapping("/{id}/graph/subgraph")
    public Result<GraphSubgraphVO> getSubgraph(@PathVariable Long id,
                                               @RequestParam(required = false) Long documentId,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(defaultValue = "2") int maxDepth,
                                               @RequestParam(defaultValue = "50") int maxNodes) {
        return Result.ok(graphService.getSubgraph(id, documentId, keyword, maxDepth, maxNodes));
    }

    @Operation(summary = "获取图谱统计信息")
    @GetMapping("/{id}/graph/stats")
    public Result<GraphStatsVO> getGraphStats(@PathVariable Long id,
                                              @RequestParam(required = false) Long documentId) {
        return Result.ok(graphService.getStats(id, documentId));
    }

    @Operation(summary = "清空知识库图谱数据（需要MANAGER及以上权限）")
    @DeleteMapping("/{id}/graph")
    public Result<Void> deleteGraph(@PathVariable Long id) {
        graphService.deleteByKnowledgeId(id);
        return Result.ok();
    }

    @Operation(summary = "删除单个文档的图谱数据（需要DEVELOPER及以上权限）")
    @DeleteMapping("/{id}/graph/documents/{documentId}")
    public Result<Void> deleteDocGraph(@PathVariable Long id, @PathVariable Long documentId) {
        graphService.deleteByDocumentId(id, documentId);
        return Result.ok();
    }

    @Operation(summary = "批量检查哪些文档已有图谱数据")
    @GetMapping("/{id}/graph/existing-docs")
    public Result<List<Long>> getExistingDocIds(@PathVariable Long id,
                                                @RequestParam List<Long> documentIds) {
        return Result.ok(graphService.getExistingDocIds(id, documentIds));
    }

    @Operation(summary = "手动创建图谱节点（需要DEVELOPER及以上权限）")
    @PostMapping("/{id}/graph/nodes")
    public Result<GraphNodeVO> createGraphNode(@PathVariable Long id,
                                               @RequestParam String name,
                                               @RequestParam(defaultValue = "其他") String entityType,
                                               @RequestParam(required = false) String description) {
        return Result.ok(graphService.createNode(id, name, entityType, description));
    }

    @Operation(summary = "删除图谱节点（需要DEVELOPER及以上权限）")
    @DeleteMapping("/{id}/graph/nodes/{elementId}")
    public Result<Void> deleteGraphNode(@PathVariable Long id, @PathVariable String elementId) {
        graphService.deleteNode(id, elementId);
        return Result.ok();
    }

    @Operation(summary = "手动创建图谱关系（需要DEVELOPER及以上权限）")
    @PostMapping("/{id}/graph/edges")
    public Result<GraphEdgeVO> createGraphEdge(@PathVariable Long id,
                                               @RequestParam String headName,
                                               @RequestParam String relationType,
                                               @RequestParam String tailName,
                                               @RequestParam(required = false) String description) {
        return Result.ok(graphService.createEdge(id, headName, relationType, tailName, description));
    }

    @Operation(summary = "删除图谱关系（需要DEVELOPER及以上权限）")
    @DeleteMapping("/{id}/graph/edges/{elementId}")
    public Result<Void> deleteGraphEdge(@PathVariable Long id, @PathVariable String elementId) {
        graphService.deleteEdge(id, elementId);
        return Result.ok();
    }

    // ========== RAG 问答 ==========

    @Operation(summary = "基于知识库RAG问答（同步）")
    @PostMapping("/{id}/ask")
    public Result<String> ask(@PathVariable Long id,
                              @RequestParam String question,
                              @RequestParam(required = false) Long providerId) {
        return Result.ok(ragService.ask(id, question, providerId));
    }

    @Operation(summary = "基于知识库RAG问答（流式）")
    @PostMapping(value = "/{id}/ask-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@PathVariable Long id,
                                @RequestParam String question,
                                @RequestParam(required = false) Long providerId) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);

        Flux<String> flux = ragService.askStream(id, question, providerId);
        flux.publishOn(Schedulers.boundedElastic())
                .subscribe(
                        chunk -> {
                            try {
                                emitter.send(chunk);
                            } catch (IOException e) {
                                // 客户端断开，忽略
                            }
                        },
                        emitter::completeWithError,
                        emitter::complete
                );

        emitter.onTimeout(emitter::complete);
        return emitter;
    }

    @Operation(summary = "检索测试（纯向量检索，不调用LLM，支持overrides覆盖参数）")
    @PostMapping("/{id}/search")
    public Result<List<RagSearchResultVO>> search(@PathVariable Long id,
                                                   @RequestBody Map<String, Object> body) {
        String question = (String) body.get("question");
        @SuppressWarnings("unchecked")
        Map<String, Object> overrides = (Map<String, Object>) body.get("overrides");
        return Result.ok(ragService.search(id, question, overrides));
    }

    // ========== 问答对 ==========

    @Operation(summary = "创建问答对（需要DEVELOPER及以上权限）")
    @PostMapping("/{id}/qa-pairs")
    public Result<QaPairVO> createQaPair(@PathVariable Long id,
                                          @Valid @RequestBody QaPairCreateDTO dto) {
        return Result.ok(qaPairService.create(id, dto));
    }

    @Operation(summary = "更新问答对（需要DEVELOPER及以上权限）")
    @PutMapping("/qa-pairs/{qaPairId}")
    public Result<QaPairVO> updateQaPair(@PathVariable Long qaPairId,
                                          @Valid @RequestBody QaPairUpdateDTO dto) {
        dto.setId(qaPairId);
        return Result.ok(qaPairService.update(dto));
    }

    @Operation(summary = "分页查询问答对列表（需要成员权限）")
    @GetMapping("/{id}/qa-pairs")
    public Result<?> listQaPairs(@PathVariable Long id,
                                  @RequestParam(defaultValue = "1") int pageNum,
                                  @RequestParam(defaultValue = "20") int pageSize,
                                  @RequestParam(required = false) String keyword) {
        return Result.ok(qaPairService.listByKnowledgeId(id, pageNum, pageSize, keyword));
    }

    @Operation(summary = "删除问答对（需要DEVELOPER及以上权限）")
    @DeleteMapping("/qa-pairs/{qaPairId}")
    public Result<Void> deleteQaPair(@PathVariable Long qaPairId) {
        qaPairService.deleteById(qaPairId);
        return Result.ok();
    }

    @Operation(summary = "批量导入问答对（需要DEVELOPER及以上权限）")
    @PostMapping("/{id}/qa-pairs/batch-import")
    public Result<Integer> batchImportQaPairs(@PathVariable Long id,
                                               @Valid @RequestBody List<QaPairCreateDTO> items) {
        return Result.ok(qaPairService.batchImport(id, items));
    }

    @Operation(summary = "AI生成问答对（异步任务，需要DEVELOPER及以上权限）")
    @PostMapping("/{id}/qa-pairs/ai-generate")
    public Result<Task> generateQaPairs(@PathVariable Long id,
                                         @RequestParam(defaultValue = "10") Integer count,
                                         @RequestParam(required = false) Long providerId,
                                         @RequestParam(required = false) String modelId) {
        return Result.ok(qaPairService.generateByAI(id, count, providerId, modelId));
    }

    @Operation(summary = "手动触发问答对向量化（需要DEVELOPER及以上权限）")
    @PostMapping("/qa-pairs/{qaPairId}/vectorize")
    public Result<Void> vectorizeQaPair(@PathVariable Long qaPairId) {
        qaPairService.vectorize(qaPairId);
        return Result.ok();
    }

    @Operation(summary = "批量触发问答对向量化（需要DEVELOPER及以上权限）")
    @PostMapping("/qa-pairs/batch-vectorize")
    public Result<Integer> batchVectorizeQaPairs(@RequestBody List<Long> qaPairIds) {
        return Result.ok(qaPairService.batchVectorize(qaPairIds));
    }
}

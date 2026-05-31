package com.lightbot.controller;

import com.lightbot.common.Result;
import com.lightbot.dto.*;
import com.lightbot.service.StandaloneGraphService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 独立知识图谱接口（全局，不关联知识库）
 *
 * @author finch
 * @since 2026-05-29
 */
@Tag(name = "独立知识图谱", description = "全局知识图谱，JSONL导入、语义搜索、节点/边CRUD")
@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class StandaloneGraphController {

    private final StandaloneGraphService standaloneGraphService;

    @Operation(summary = "从JSONL文件导入三元组")
    @PostMapping("/import/jsonl")
    public Result<GraphStatsVO> importFromJsonl(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long providerId) {
        return Result.ok(standaloneGraphService.importFromJsonl(file, providerId));
    }

    @Operation(summary = "手动批量导入三元组")
    @PostMapping("/import")
    public Result<GraphStatsVO> importTriples(
            @RequestBody @jakarta.validation.Valid GraphImportRequest request,
            @RequestParam(required = false) Long providerId) {
        return Result.ok(standaloneGraphService.importTriples(request.getTriples(), providerId));
    }

    @Operation(summary = "获取子图数据")
    @GetMapping("/subgraph")
    public Result<GraphSubgraphVO> getSubgraph(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "2") int maxDepth,
            @RequestParam(defaultValue = "50") int maxNodes) {
        return Result.ok(standaloneGraphService.getSubgraph(keyword, maxDepth, maxNodes));
    }

    @Operation(summary = "语义搜索节点")
    @GetMapping("/search")
    public Result<List<GraphNodeVO>> semanticSearch(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int topK,
            @RequestParam(required = false) Long providerId) {
        return Result.ok(standaloneGraphService.semanticSearch(query, topK, providerId));
    }

    @Operation(summary = "获取图谱统计")
    @GetMapping("/stats")
    public Result<GraphStatsVO> getStats() {
        return Result.ok(standaloneGraphService.getStats());
    }

    @Operation(summary = "清空独立图谱")
    @DeleteMapping
    public Result<Void> deleteAll() {
        standaloneGraphService.deleteAll();
        return Result.ok();
    }

    @Operation(summary = "创建节点")
    @PostMapping("/nodes")
    public Result<GraphNodeVO> createNode(
            @RequestParam String name,
            @RequestParam(defaultValue = "其他") String entityType,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Long providerId) {
        return Result.ok(standaloneGraphService.createNode(name, entityType, description, providerId));
    }

    @Operation(summary = "更新节点")
    @PutMapping("/nodes/{elementId}")
    public Result<GraphNodeVO> updateNode(
            @PathVariable String elementId,
            @RequestParam String name,
            @RequestParam(defaultValue = "其他") String entityType,
            @RequestParam(required = false) String description) {
        return Result.ok(standaloneGraphService.updateNode(elementId, name, entityType, description));
    }

    @Operation(summary = "删除节点")
    @DeleteMapping("/nodes/{elementId}")
    public Result<Void> deleteNode(@PathVariable String elementId) {
        standaloneGraphService.deleteNode(elementId);
        return Result.ok();
    }

    @Operation(summary = "创建关系")
    @PostMapping("/edges")
    public Result<GraphEdgeVO> createEdge(
            @RequestParam String headName,
            @RequestParam String relationType,
            @RequestParam String tailName,
            @RequestParam(required = false) String description) {
        return Result.ok(standaloneGraphService.createEdge(headName, relationType, tailName, description));
    }

    @Operation(summary = "更新关系")
    @PutMapping("/edges/{elementId}")
    public Result<GraphEdgeVO> updateEdge(
            @PathVariable String elementId,
            @RequestParam(required = false) String relationType,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Double weight) {
        return Result.ok(standaloneGraphService.updateEdge(elementId, relationType, description, weight));
    }

    @Operation(summary = "删除关系")
    @DeleteMapping("/edges/{elementId}")
    public Result<Void> deleteEdge(@PathVariable String elementId) {
        standaloneGraphService.deleteEdge(elementId);
        return Result.ok();
    }

    @Operation(summary = "获取所有节点名称列表")
    @GetMapping("/node-names")
    public Result<List<String>> listNodeNames() {
        return Result.ok(standaloneGraphService.listNodeNames());
    }

    @Operation(summary = "重建向量索引")
    @PostMapping("/rebuild-index")
    public Result<Integer> rebuildVectorIndex() {
        return Result.ok(standaloneGraphService.rebuildVectorIndex());
    }
}

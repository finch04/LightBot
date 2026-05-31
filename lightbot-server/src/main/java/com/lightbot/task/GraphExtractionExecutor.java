package com.lightbot.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.dto.GraphStatsVO;
import com.lightbot.dto.GraphTripleDTO;
import com.lightbot.entity.Chunk;
import com.lightbot.entity.Document;
import com.lightbot.entity.GraphDocument;
import com.lightbot.entity.Knowledge;
import com.lightbot.entity.KnowledgeGraph;
import com.lightbot.entity.Task;
import com.lightbot.enums.GraphTaskStatus;
import com.lightbot.mapper.GraphDocumentMapper;
import com.lightbot.mapper.KnowledgeGraphMapper;
import com.lightbot.model.graph.GraphExtractor;
import com.lightbot.service.ChunkService;
import com.lightbot.service.DocumentService;
import com.lightbot.service.KnowledgeService;
import com.lightbot.service.TaskService;
import com.lightbot.util.Neo4jUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 图谱抽取任务执行器
 *
 * @author finch
 * @since 2026-05-29
 */
@Slf4j
@Component("graphExtractionExecutor")
@RequiredArgsConstructor
public class GraphExtractionExecutor implements TaskExecutor {

    private final GraphExtractor graphExtractor;
    private final Neo4jUtil neo4jUtil;
    private final KnowledgeService knowledgeService;
    private final DocumentService documentService;
    private final ChunkService chunkService;
    private final TaskService taskService;
    private final KnowledgeGraphMapper knowledgeGraphMapper;
    private final GraphDocumentMapper graphDocumentMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String execute(Task task) throws Exception {
        if (!neo4jUtil.isAvailable()) {
            throw new RuntimeException("Neo4j 图数据库不可用，无法执行图谱抽取");
        }

        JsonNode payload = objectMapper.readTree(task.getPayload());
        Long knowledgeGraphId = payload.get("knowledgeGraphId").asLong();
        Long knowledgeId = payload.get("knowledgeId").asLong();
        Long providerId = payload.has("providerId") ? payload.get("providerId").asLong() : null;
        String modelId = payload.has("modelId") ? payload.get("modelId").asText("") : "";
        if (modelId.isBlank()) modelId = null;
        String providerName = payload.has("providerName") ? payload.get("providerName").asText("") : "";

        // 1. 解析文档ID列表和图谱文档关联ID列表
        List<Long> documentIds = new ArrayList<>();
        List<Long> graphDocIds = new ArrayList<>();
        if (payload.has("documentIds")) {
            for (JsonNode node : payload.get("documentIds")) {
                documentIds.add(node.asLong());
            }
        }
        if (payload.has("graphDocIds")) {
            for (JsonNode node : payload.get("graphDocIds")) {
                graphDocIds.add(node.asLong());
            }
        }

        // 2. 加载 KnowledgeGraph 记录
        KnowledgeGraph kg = knowledgeGraphMapper.selectById(knowledgeGraphId);
        if (kg == null) {
            throw new RuntimeException("知识图谱记录不存在: " + knowledgeGraphId);
        }
        kg.setStatus(GraphTaskStatus.RUNNING);
        knowledgeGraphMapper.updateById(kg);

        // 3. 加载 GraphDocument 记录并标记为运行中
        List<GraphDocument> graphDocs = new ArrayList<>();
        for (Long gdId : graphDocIds) {
            GraphDocument gd = graphDocumentMapper.selectById(gdId);
            if (gd != null) {
                gd.setStatus(GraphTaskStatus.RUNNING);
                graphDocumentMapper.updateById(gd);
                graphDocs.add(gd);
            }
        }

        String modelInfo = providerName.isBlank() ? String.valueOf(providerId) : providerName + (modelId != null ? "/" + modelId : "");
        log.info("[图谱抽取执行器] 开始, taskId={}, knowledgeId={}, model={}, documentIds={}, graphDocIds={}",
                task.getId(), knowledgeId, modelInfo, documentIds, graphDocIds);

        var tracker = new TaskProgressTracker(taskService, task.getId())
                .phases("获取文档", "抽取实体关系", "写入图谱", "更新统计");

        try {
            // 4. 获取待处理的文档列表
            tracker.nextPhase("正在获取文档列表...");
            List<Document> documents = getDocuments(knowledgeId, documentIds);
            if (documents.isEmpty()) {
                throw new RuntimeException("没有可处理的已完成文档");
            }

            // 5. 构建 documentId -> GraphDocument 映射
            Map<Long, GraphDocument> docGraphDocMap = new HashMap<>();
            for (GraphDocument gd : graphDocs) {
                if (gd.getDocumentId() != null) {
                    docGraphDocMap.put(gd.getDocumentId(), gd);
                }
            }
            // 全量抽取时只有一个 GraphDocument（或没有），所有文档共用
            GraphDocument fallbackGd = graphDocs.size() == 1 ? graphDocs.get(0) : null;

            String label = Neo4jUtil.kbLabel(knowledgeId);
            int totalNodes = 0, totalEdges = 0;

            // 6. 多文档合并抽取：先清空合并图谱数据
            boolean isMultiDoc = documentIds != null && documentIds.size() > 1;
            if (isMultiDoc || documentIds == null) {
                tracker.nextPhase("正在清空旧图谱数据...");
                clearAllGraphData(label);
                log.info("[图谱抽取执行器] 已清空合并图谱数据: knowledgeId={}, label={}", knowledgeId, label);
            }

            // 7. 逐文档处理
            String graphSource = (isMultiDoc || documentIds == null) ? "merged" : "single_doc";
            var docProgress = tracker.subRange(25, 75, documents.size());
            for (int docIdx = 0; docIdx < documents.size(); docIdx++) {
                Document doc = documents.get(docIdx);
                GraphDocument docGraphDoc = docGraphDocMap.getOrDefault(doc.getId(), fallbackGd);

                try {
                    // 7.1 单文档抽取时才逐文档删除；多文档合并抽取已在步骤6清空
                    if (!isMultiDoc && documentIds != null) {
                        deleteDocGraphFromNeo4j(label, doc.getId());
                    }

                    List<Chunk> docChunks = chunkService.listByDocumentId(doc.getId());
                    log.info("[图谱抽取执行器] 文档 {}/{}: docId={}, chunks={}", docIdx + 1, documents.size(), doc.getId(), docChunks.size());

                    List<GraphTripleDTO> docTriples = extractTriples(task, docChunks, providerId, modelId);

                    int[] counts = writeTriplesToNeo4j(label, docTriples, doc.getId(), knowledgeId, "auto", graphSource);
                    totalNodes += counts[0];
                    totalEdges += counts[1];

                    docProgress.tick("文档 %d/%d 抽取完成".formatted(docIdx + 1, documents.size()));

                    // 更新 GraphDocument 状态
                    if (docGraphDoc != null) {
                        docGraphDoc.setStatus(GraphTaskStatus.COMPLETED);
                        docGraphDoc.setEntityCount(counts[0]);
                        docGraphDoc.setRelationCount(counts[1]);
                        graphDocumentMapper.updateById(docGraphDoc);
                    }

                } catch (Exception e) {
                    log.warn("[图谱抽取执行器] 文档 {} 抽取失败: {}", doc.getId(), e.getMessage());
                    if (docGraphDoc != null) {
                        docGraphDoc.setStatus(GraphTaskStatus.FAILED);
                        docGraphDoc.setErrorMessage(e.getMessage());
                        graphDocumentMapper.updateById(docGraphDoc);
                    }
                }
            }

            // 8. 更新知识库统计
            tracker.nextPhase("正在更新统计...");
            GraphStatsVO stats = getStatsFromNeo4j(label);
            Knowledge knowledge = knowledgeService.getById(knowledgeId);
            if (knowledge != null) {
                knowledge.setNodeCount(stats.getNodeCount());
                knowledge.setEdgeCount(stats.getEdgeCount());
                knowledgeService.updateById(knowledge);
            }

            // 9. 更新 KnowledgeGraph 状态
            kg.setStatus(GraphTaskStatus.COMPLETED);
            kg.setNodeCount(stats.getNodeCount());
            kg.setEdgeCount(stats.getEdgeCount());
            kg.setTaskId(null);
            kg.setErrorMessage(null);
            knowledgeGraphMapper.updateById(kg);

            tracker.update(100, "图谱抽取完成");
            log.info("[图谱抽取执行器] 完成, knowledgeId={}, nodes={}, edges={}", knowledgeId, totalNodes, totalEdges);

            return "图谱抽取完成, knowledgeId=%d, nodes=%d, edges=%d".formatted(knowledgeId, totalNodes, totalEdges);

        } catch (Exception e) {
            // 全局失败：更新 KnowledgeGraph 和未完成的 GraphDocument
            kg.setStatus(GraphTaskStatus.FAILED);
            kg.setErrorMessage(e.getMessage());
            kg.setTaskId(null);
            knowledgeGraphMapper.updateById(kg);

            for (GraphDocument gd : graphDocs) {
                if (gd.getStatus() == GraphTaskStatus.PENDING || gd.getStatus() == GraphTaskStatus.RUNNING) {
                    gd.setStatus(GraphTaskStatus.FAILED);
                    gd.setErrorMessage(e.getMessage());
                    graphDocumentMapper.updateById(gd);
                }
            }
            throw e;
        }
    }

    private List<GraphTripleDTO> extractTriples(Task task, List<Chunk> chunks, Long providerId, String modelId) throws Exception {
        if (chunks.isEmpty()) {
            return new ArrayList<>();
        }

        List<GraphTripleDTO> allTriples = Collections.synchronizedList(new ArrayList<>());
        int batchSize = 3;
        int parallelism = 5;

        List<List<Chunk>> batches = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i += batchSize) {
            int end = Math.min(i + batchSize, chunks.size());
            batches.add(new ArrayList<>(chunks.subList(i, end)));
        }

        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        AtomicInteger completedChunks = new AtomicInteger(0);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (List<Chunk> batch : batches) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                Task latest = taskService.getById(task.getId());
                if (latest != null && latest.getCancelRequested() == 1) {
                    throw new CompletionException(new RuntimeException("任务已被用户取消"));
                }

                try {
                    List<String> contents = batch.stream().map(Chunk::getContent).collect(Collectors.toList());
                    List<GraphTripleDTO> triples = graphExtractor.extractBatch(contents, providerId, modelId);
                    allTriples.addAll(triples);
                } catch (Exception e) {
                    log.warn("[图谱抽取执行器] 批次抽取失败, 降级为逐个抽取: {}", e.getMessage());
                    for (Chunk chunk : batch) {
                        try {
                            allTriples.addAll(graphExtractor.extract(chunk.getContent(), providerId, modelId));
                        } catch (Exception ex) {
                            log.warn("[图谱抽取执行器] Chunk {} 抽取失败: {}", chunk.getId(), ex.getMessage());
                        }
                    }
                }

                completedChunks.addAndGet(batch.size());
            }, executor);
            futures.add(future);
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException && "任务已被用户取消".equals(e.getCause().getMessage())) {
                throw new RuntimeException("任务已被用户取消");
            }
            throw e;
        } finally {
            executor.shutdown();
        }

        return allTriples;
    }

    private List<Document> getDocuments(Long knowledgeId, List<Long> documentIds) {
        if (documentIds != null && !documentIds.isEmpty()) {
            return documentService.listByIds(documentIds);
        }
        return documentService.list(new LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeId, knowledgeId)
                .eq(Document::getStatus, com.lightbot.enums.DocumentStatus.COMPLETED));
    }

    private int[] writeTriplesToNeo4j(String label, List<GraphTripleDTO> triples, Long documentId, Long knowledgeId, String source, String graphSource) {
        java.util.Set<String> nodes = new java.util.HashSet<>();
        int edgeCount = 0;

        for (GraphTripleDTO triple : triples) {
            if (triple.getHead() == null || triple.getTail() == null || triple.getRelation() == null) {
                continue;
            }

            String nodeCypher = """
                MERGE (n:Entity:`%s` {name: $name})
                ON CREATE SET n.id = $nodeId, n.entity_type = $entityType, n.description = $description,
                              n.source = $source, n.document_id = $documentId, n.knowledge_id = $knowledgeId,
                              n.graph_source = $graphSource,
                              n.created_at = datetime(), n.updated_at = datetime()
                ON MATCH SET n.updated_at = datetime()
                """.formatted(label);

            java.util.Map<String, Object> nodeParams = new java.util.LinkedHashMap<>();
            nodeParams.put("name", triple.getHead());
            nodeParams.put("nodeId", String.valueOf(snowflakeId()));
            nodeParams.put("entityType", triple.getHeadType() != null ? triple.getHeadType() : "其他");
            nodeParams.put("description", triple.getHeadDesc() != null ? triple.getHeadDesc() : "");
            nodeParams.put("source", source);
            nodeParams.put("documentId", documentId != null ? String.valueOf(documentId) : "");
            nodeParams.put("knowledgeId", String.valueOf(knowledgeId));
            nodeParams.put("graphSource", graphSource);
            neo4jUtil.run(nodeCypher, nodeParams);
            nodes.add(triple.getHead());

            java.util.Map<String, Object> nodeParams2 = new java.util.LinkedHashMap<>();
            nodeParams2.put("name", triple.getTail());
            nodeParams2.put("nodeId", String.valueOf(snowflakeId()));
            nodeParams2.put("entityType", triple.getTailType() != null ? triple.getTailType() : "其他");
            nodeParams2.put("description", triple.getTailDesc() != null ? triple.getTailDesc() : "");
            nodeParams2.put("source", source);
            nodeParams2.put("documentId", documentId != null ? String.valueOf(documentId) : "");
            nodeParams2.put("knowledgeId", String.valueOf(knowledgeId));
            nodeParams2.put("graphSource", graphSource);
            neo4jUtil.run(nodeCypher, nodeParams2);
            nodes.add(triple.getTail());

            String relCypher = """
                MATCH (h:Entity:`%s` {name: $head})
                MATCH (t:Entity:`%s` {name: $tail})
                MERGE (h)-[r:RELATION {relation_type: $relation}]->(t)
                ON CREATE SET r.id = $relId, r.description = $relDesc, r.weight = 1.0,
                              r.source = $source, r.knowledge_id = $knowledgeId,
                              r.graph_source = $graphSource,
                              r.created_at = datetime(), r.updated_at = datetime()
                ON MATCH SET r.weight = r.weight + 0.1, r.updated_at = datetime()
                """.formatted(label, label);

            java.util.Map<String, Object> relParams = new java.util.LinkedHashMap<>();
            relParams.put("head", triple.getHead());
            relParams.put("tail", triple.getTail());
            relParams.put("relation", triple.getRelation());
            relParams.put("relId", String.valueOf(snowflakeId()));
            relParams.put("relDesc", triple.getRelationDesc() != null ? triple.getRelationDesc() : "");
            relParams.put("source", source);
            relParams.put("knowledgeId", String.valueOf(knowledgeId));
            relParams.put("graphSource", graphSource);
            neo4jUtil.run(relCypher, relParams);
            edgeCount++;
        }

        return new int[]{nodes.size(), edgeCount};
    }

    /**
     * 清空合并图谱数据（不影响单文档图谱）
     */
    private void clearAllGraphData(String label) {
        String cypher = """
            MATCH (n:Entity:`%s`)
            WHERE n.graph_source = 'merged' OR n.graph_source IS NULL
            DETACH DELETE n
            """.formatted(label);
        neo4jUtil.run(cypher, java.util.Map.of());
    }

    /**
     * 删除指定文档的单文档图谱数据
     */
    private void deleteDocGraphFromNeo4j(String label, Long documentId) {
        String docIdStr = String.valueOf(documentId);
        // 删除该文档节点的所有关系（仅单文档图谱）
        String deleteRels = """
            MATCH (n:Entity:`%s`)-[r:RELATION]-(m:Entity:`%s`)
            WHERE n.document_id = $docId AND n.graph_source = 'single_doc'
            DELETE r
            """.formatted(label, label);
        neo4jUtil.run(deleteRels, java.util.Map.of("docId", docIdStr));

        // 删除该文档的节点（仅单文档图谱）
        String deleteNodes = """
            MATCH (n:Entity:`%s`)
            WHERE n.document_id = $docId AND n.graph_source = 'single_doc'
            DELETE n
            """.formatted(label);
        neo4jUtil.run(deleteNodes, java.util.Map.of("docId", docIdStr));
    }

    private GraphStatsVO getStatsFromNeo4j(String label) {
        String cypher = """
            MATCH (n:Entity:`%s`)
            WHERE n.graph_source = 'merged' OR n.graph_source IS NULL
            WITH count(n) AS nodeCount
            OPTIONAL MATCH (:Entity:`%s`)-[r:RELATION]->(:Entity:`%s`)
            WHERE r.graph_source = 'merged' OR r.graph_source IS NULL
            RETURN nodeCount, count(r) AS edgeCount
            """.formatted(label, label, label);

        var records = neo4jUtil.query(cypher, java.util.Map.of());
        int nodeCount = 0, edgeCount = 0;
        if (!records.isEmpty()) {
            var r = records.get(0);
            nodeCount = r.get("nodeCount").asInt();
            edgeCount = r.get("edgeCount").asInt();
        }
        return new GraphStatsVO(nodeCount, edgeCount, java.util.Map.of());
    }

    private long snowflakeId() {
        return java.util.concurrent.ThreadLocalRandom.current().nextLong(1000000000000000000L, Long.MAX_VALUE);
    }
}

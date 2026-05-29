package com.lightbot.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.dto.GraphStatsVO;
import com.lightbot.dto.GraphTripleDTO;
import com.lightbot.entity.Chunk;
import com.lightbot.entity.Document;
import com.lightbot.entity.GraphExtractionTask;
import com.lightbot.entity.Knowledge;
import com.lightbot.entity.Task;
import com.lightbot.enums.GraphTaskStatus;
import com.lightbot.mapper.GraphExtractionTaskMapper;
import com.lightbot.model.graph.GraphExtractor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.List;
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
    private final GraphExtractionTaskMapper graphExtractionTaskMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String execute(Task task) throws Exception {
        // 检查 Neo4j 可用性
        if (!neo4jUtil.isAvailable()) {
            throw new RuntimeException("Neo4j 图数据库不可用，无法执行图谱抽取");
        }

        JsonNode payload = objectMapper.readTree(task.getPayload());
        Long graphTaskId = payload.get("graphTaskId").asLong();
        Long knowledgeId = payload.get("knowledgeId").asLong();
        Long documentId = payload.has("documentId") ? payload.get("documentId").asLong() : null;
        Long providerId = payload.has("providerId") ? payload.get("providerId").asLong() : null;

        // 1. 更新图谱任务状态为运行中
        GraphExtractionTask graphTask = graphExtractionTaskMapper.selectById(graphTaskId);
        if (graphTask == null) {
            throw new RuntimeException("图谱抽取任务不存在: " + graphTaskId);
        }
        graphTask.setStatus(GraphTaskStatus.RUNNING);
        graphExtractionTaskMapper.updateById(graphTask);

        log.info("[图谱抽取执行器] 开始, taskId={}, knowledgeId={}, documentId={}", task.getId(), knowledgeId, documentId);

        var tracker = new TaskProgressTracker(taskService, task.getId())
                .phases("获取文档", "抽取实体关系", "写入图谱", "更新统计");

        try {
            // 2. 获取待处理的文档列表
            tracker.nextPhase("正在获取文档列表...");
            List<Document> documents = getDocuments(knowledgeId, documentId);
            if (documents.isEmpty()) {
                throw new RuntimeException("没有可处理的已完成文档");
            }

            // 3. 获取所有 Chunk
            List<Chunk> allChunks = new ArrayList<>();
            for (Document doc : documents) {
                allChunks.addAll(chunkService.listByDocumentId(doc.getId()));
            }
            log.info("[图谱抽取执行器] 待处理: documents={}, chunks={}", documents.size(), allChunks.size());

            // 4. 并行批量抽取三元组（batchSize=3, parallelism=5, 预期提速~10x）
            tracker.nextPhase("正在抽取实体关系 (0/" + allChunks.size() + ")...");
            TaskProgressTracker.SubProgress sub = tracker.subRange(20, 70, allChunks.size());

            List<GraphTripleDTO> allTriples = Collections.synchronizedList(new ArrayList<>());
            int batchSize = 3;
            int parallelism = 5;

            // 将chunks分批
            List<List<Chunk>> batches = new ArrayList<>();
            for (int i = 0; i < allChunks.size(); i += batchSize) {
                int end = Math.min(i + batchSize, allChunks.size());
                batches.add(new ArrayList<>(allChunks.subList(i, end)));
            }

            ExecutorService executor = Executors.newFixedThreadPool(parallelism);
            AtomicInteger completedChunks = new AtomicInteger(0);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (List<Chunk> batch : batches) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    // 检查取消请求
                    Task latest = taskService.getById(task.getId());
                    if (latest != null && latest.getCancelRequested() == 1) {
                        throw new CompletionException(new RuntimeException("任务已被用户取消"));
                    }

                    try {
                        List<String> contents = batch.stream()
                                .map(Chunk::getContent)
                                .collect(Collectors.toList());
                        List<GraphTripleDTO> triples = graphExtractor.extractBatch(contents, providerId);
                        allTriples.addAll(triples);
                    } catch (Exception e) {
                        log.warn("[图谱抽取执行器] 批次抽取失败, 降级为逐个抽取: {}", e.getMessage());
                        for (Chunk chunk : batch) {
                            try {
                                List<GraphTripleDTO> triples = graphExtractor.extract(chunk.getContent(), providerId);
                                allTriples.addAll(triples);
                            } catch (Exception ex) {
                                log.warn("[图谱抽取执行器] Chunk {} 抽取失败: {}", chunk.getId(), ex.getMessage());
                            }
                        }
                    }

                    int done = completedChunks.addAndGet(batch.size());
                    sub.setCompleted(done, "正在抽取实体关系 (" + done + "/" + allChunks.size() + ")...");
                }, executor);
                futures.add(future);
            }

            // 等待所有批次完成
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

            log.info("[图谱抽取执行器] 抽取完成, 三元组总数={}", allTriples.size());

            // 5. 写入 Neo4j
            tracker.nextPhase("正在写入图谱...");
            String label = Neo4jUtil.kbLabel(knowledgeId);

            // 按文档分组写入
            int totalNodes = 0, totalEdges = 0;
            for (Document doc : documents) {
                List<GraphTripleDTO> docTriples = new ArrayList<>();
                // 简化：将所有三元组都关联到第一个文档（实际可按 chunk 来源分组）
                if (doc.equals(documents.get(0))) {
                    docTriples = allTriples;
                }
                if (!docTriples.isEmpty()) {
                    int[] counts = writeTriplesToNeo4j(label, docTriples, doc.getId(), knowledgeId, "auto");
                    totalNodes += counts[0];
                    totalEdges += counts[1];
                }
            }

            // 6. 更新统计
            tracker.nextPhase("正在更新统计...");
            GraphStatsVO stats = getStatsFromNeo4j(label);
            Knowledge knowledge = knowledgeService.getById(knowledgeId);
            if (knowledge != null) {
                knowledge.setNodeCount(stats.getNodeCount());
                knowledge.setEdgeCount(stats.getEdgeCount());
                knowledgeService.updateById(knowledge);
            }

            // 7. 更新图谱任务状态
            graphTask.setStatus(GraphTaskStatus.COMPLETED);
            graphTask.setEntityCount(totalNodes);
            graphTask.setRelationCount(totalEdges);
            graphExtractionTaskMapper.updateById(graphTask);

            tracker.update(100, "图谱抽取完成");
            log.info("[图谱抽取执行器] 完成, knowledgeId={}, nodes={}, edges={}", knowledgeId, totalNodes, totalEdges);

            return "图谱抽取完成, knowledgeId=%d, nodes=%d, edges=%d".formatted(knowledgeId, totalNodes, totalEdges);

        } catch (Exception e) {
            // 更新图谱任务状态为失败
            graphTask.setStatus(GraphTaskStatus.FAILED);
            graphTask.setErrorMessage(e.getMessage());
            graphExtractionTaskMapper.updateById(graphTask);
            throw e;
        }
    }

    /**
     * 获取待处理的文档列表（后台任务，跳过权限校验，权限已在 Controller 层校验）
     */
    private List<Document> getDocuments(Long knowledgeId, Long documentId) {
        if (documentId != null) {
            Document doc = documentService.getById(documentId);
            return doc != null ? List.of(doc) : List.of();
        }
        // 全量：直接查询已完成文档，不走 listByKnowledgeId（含 Sa-Token 权限校验，后台线程无法使用）
        return documentService.list(new LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeId, knowledgeId)
                .eq(Document::getStatus, com.lightbot.enums.DocumentStatus.COMPLETED));
    }

    // 复用 GraphServiceImpl 中的写入逻辑（简化版，直接内联）
    private int[] writeTriplesToNeo4j(String label, List<GraphTripleDTO> triples, Long documentId, Long knowledgeId, String source) {
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
                              n.created_at = datetime(), n.updated_at = datetime()
                ON MATCH SET n.updated_at = datetime()
                """.formatted(label);

            neo4jUtil.run(nodeCypher, java.util.Map.of(
                    "name", triple.getHead(), "nodeId", String.valueOf(snowflakeId()),
                    "entityType", triple.getHeadType() != null ? triple.getHeadType() : "其他",
                    "description", triple.getHeadDesc() != null ? triple.getHeadDesc() : "",
                    "source", source, "documentId", documentId != null ? String.valueOf(documentId) : "",
                    "knowledgeId", String.valueOf(knowledgeId)));
            nodes.add(triple.getHead());

            neo4jUtil.run(nodeCypher, java.util.Map.of(
                    "name", triple.getTail(), "nodeId", String.valueOf(snowflakeId()),
                    "entityType", triple.getTailType() != null ? triple.getTailType() : "其他",
                    "description", triple.getTailDesc() != null ? triple.getTailDesc() : "",
                    "source", source, "documentId", documentId != null ? String.valueOf(documentId) : "",
                    "knowledgeId", String.valueOf(knowledgeId)));
            nodes.add(triple.getTail());

            String relCypher = """
                MATCH (h:Entity:`%s` {name: $head})
                MATCH (t:Entity:`%s` {name: $tail})
                MERGE (h)-[r:RELATION {relation_type: $relation}]->(t)
                ON CREATE SET r.id = $relId, r.description = $relDesc, r.weight = 1.0,
                              r.source = $source, r.knowledge_id = $knowledgeId,
                              r.created_at = datetime(), r.updated_at = datetime()
                ON MATCH SET r.weight = r.weight + 0.1, r.updated_at = datetime()
                """.formatted(label, label);

            neo4jUtil.run(relCypher, java.util.Map.of(
                    "head", triple.getHead(), "tail", triple.getTail(), "relation", triple.getRelation(),
                    "relId", String.valueOf(snowflakeId()),
                    "relDesc", triple.getRelationDesc() != null ? triple.getRelationDesc() : "",
                    "source", source, "knowledgeId", String.valueOf(knowledgeId)));
            edgeCount++;
        }

        return new int[]{nodes.size(), edgeCount};
    }

    private GraphStatsVO getStatsFromNeo4j(String label) {
        String cypher = """
            MATCH (n:Entity:`%s`)
            WITH count(n) AS nodeCount
            OPTIONAL MATCH (:Entity:`%s`)-[r:RELATION]->(:Entity:`%s`)
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

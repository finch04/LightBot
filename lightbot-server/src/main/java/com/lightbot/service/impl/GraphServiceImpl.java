package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.*;
import com.lightbot.entity.GraphDocument;
import com.lightbot.entity.Knowledge;
import com.lightbot.entity.KnowledgeGraph;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.GraphTaskStatus;
import com.lightbot.mapper.GraphDocumentMapper;
import com.lightbot.mapper.KnowledgeGraphMapper;
import com.lightbot.model.graph.GraphExtractor;
import com.lightbot.enums.KnowledgeRole;
import com.lightbot.entity.ModelProvider;
import com.lightbot.service.GraphService;
import com.lightbot.service.KnowledgePermissionHelper;
import com.lightbot.service.KnowledgeService;
import com.lightbot.service.ModelProviderService;
import com.lightbot.service.TaskService;
import com.lightbot.util.Neo4jUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识图谱服务实现类
 *
 * @author finch
 * @since 2026-05-29
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphServiceImpl implements GraphService {

    private final Neo4jUtil neo4jUtil;
    private final KnowledgeService knowledgeService;
    private final KnowledgePermissionHelper permissionHelper;
    private final GraphExtractor graphExtractor;
    private final TaskService taskService;
    private final ModelProviderService modelProviderService;
    private final KnowledgeGraphMapper knowledgeGraphMapper;
    private final GraphDocumentMapper graphDocumentMapper;
    private final ObjectMapper objectMapper;

    private void checkNeo4jAvailable() {
        if (!neo4jUtil.isAvailable()) {
            throw new BizException(ErrorCode.GRAPH_NEO4J_UNAVAILABLE);
        }
    }

    // ==================== 抽取 ====================

    @Override
    public Long extractFromDocument(Long knowledgeId, List<Long> documentIds, Long providerId, String modelId) {
        checkNeo4jAvailable();
        permissionHelper.checkPermission(knowledgeId, KnowledgeRole.DEVELOPER);

        Knowledge knowledge = knowledgeService.getById(knowledgeId);
        if (knowledge == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NOT_FOUND);
        }

        // 1. 获取或创建 KnowledgeGraph 记录
        KnowledgeGraph kg = getOrCreateKnowledgeGraph(knowledgeId);

        boolean isPartial = documentIds != null && !documentIds.isEmpty();
        boolean isSingleDoc = isPartial && documentIds.size() == 1;

        // 2. 幂等检查 + 构建 payload
        List<Long> targetDocIds;
        List<Long> graphDocIds = new ArrayList<>();

        if (isSingleDoc) {
            // 单文档抽取：创建/更新 GraphDocument 记录用于状态跟踪
            targetDocIds = filterRunningDocs(kg.getId(), documentIds);
            if (targetDocIds.isEmpty()) {
                log.info("[图谱] 所有文档均已有运行中的任务, 复用: knowledgeId={}", knowledgeId);
                return kg.getTaskId() != null ? kg.getTaskId() : kg.getId();
            }
            // 查询已有的 GraphDocument 记录（包括已完成/失败的）
            List<GraphDocument> existingGds = graphDocumentMapper.selectList(
                    new LambdaQueryWrapper<GraphDocument>()
                            .eq(GraphDocument::getGraphId, kg.getId())
                            .in(GraphDocument::getDocumentId, targetDocIds));
            Map<Long, GraphDocument> existingMap = existingGds.stream()
                    .collect(Collectors.toMap(GraphDocument::getDocumentId, gd -> gd));

            for (Long docId : targetDocIds) {
                GraphDocument existing = existingMap.get(docId);
                if (existing != null) {
                    // 已有记录：重置状态为 PENDING
                    existing.setStatus(GraphTaskStatus.PENDING);
                    existing.setEntityCount(0);
                    existing.setRelationCount(0);
                    existing.setErrorMessage(null);
                    graphDocumentMapper.updateById(existing);
                    graphDocIds.add(existing.getId());
                } else {
                    // 新记录：插入
                    GraphDocument gd = new GraphDocument();
                    gd.setGraphId(kg.getId());
                    gd.setDocumentId(docId);
                    gd.setStatus(GraphTaskStatus.PENDING);
                    gd.setEntityCount(0);
                    gd.setRelationCount(0);
                    graphDocumentMapper.insert(gd);
                    graphDocIds.add(gd.getId());
                }
            }
        } else if (isPartial) {
            // 多文档合并抽取：不创建 GraphDocument 记录，只做幂等检查
            if (kg.getStatus() == GraphTaskStatus.PENDING || kg.getStatus() == GraphTaskStatus.RUNNING) {
                log.info("[图谱] 已有抽取任务在运行, 复用: knowledgeId={}, taskId={}", knowledgeId, kg.getTaskId());
                return kg.getTaskId() != null ? kg.getTaskId() : kg.getId();
            }
            targetDocIds = documentIds;
        } else {
            // 全量抽取：检查是否已有全量任务在运行
            if (kg.getStatus() == GraphTaskStatus.PENDING || kg.getStatus() == GraphTaskStatus.RUNNING) {
                log.info("[图谱] 已有抽取任务在运行, 复用: knowledgeId={}, taskId={}", knowledgeId, kg.getTaskId());
                return kg.getTaskId() != null ? kg.getTaskId() : kg.getId();
            }
            targetDocIds = null;
        }

        // 3. 更新 KnowledgeGraph 状态
        kg.setStatus(GraphTaskStatus.PENDING);
        kg.setErrorMessage(null);
        knowledgeGraphMapper.updateById(kg);

        // 4. 构建异步任务 payload
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("knowledgeGraphId", kg.getId());
            payload.put("knowledgeId", knowledgeId);
            if (targetDocIds != null) {
                payload.put("documentIds", targetDocIds);
                if (!graphDocIds.isEmpty()) {
                    payload.put("graphDocIds", graphDocIds);
                }
            }
            if (providerId != null) {
                payload.put("providerId", providerId);
                ModelProvider provider = modelProviderService.getById(providerId);
                if (provider != null) {
                    payload.put("providerName", provider.getName());
                }
            }
            if (modelId != null && !modelId.isBlank()) {
                payload.put("modelId", modelId);
            }
            var task = taskService.createTask(com.lightbot.enums.TaskType.GRAPH_EXTRACTION,
                    "图谱抽取", knowledge.getUserId(), knowledgeId,
                    objectMapper.writeValueAsString(payload));

            // 记录 taskId 到 KnowledgeGraph
            kg.setTaskId(task.getId());
            knowledgeGraphMapper.updateById(kg);

            log.info("[图谱] 抽取任务已提交: knowledgeId={}, documentIds={}, taskId={}", knowledgeId, documentIds, task.getId());
            return task.getId();
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }

    // ==================== 导入 ====================

    @Override
    public GraphStatsVO importTriples(Long knowledgeId, List<GraphTripleDTO> triples, Long providerId) {
        checkNeo4jAvailable();
        permissionHelper.checkPermission(knowledgeId, KnowledgeRole.DEVELOPER);

        Knowledge knowledge = knowledgeService.getById(knowledgeId);
        if (knowledge == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NOT_FOUND);
        }

        String label = Neo4jUtil.kbLabel(knowledgeId);
        int[] counts = writeTriplesToNeo4j(label, triples, null, knowledgeId, "import");
        updateKnowledgeGraphStats(knowledgeId, label);

        log.info("[图谱] 导入完成: knowledgeId={}, nodes={}, edges={}", knowledgeId, counts[0], counts[1]);
        return new GraphStatsVO(counts[0], counts[1], Map.of());
    }

    // ==================== 查询 ====================

    @Override
    public GraphSubgraphVO getSubgraph(Long knowledgeId, Long documentId, String keyword, int maxDepth, int maxNodes) {
        permissionHelper.checkMember(knowledgeId);
        if (!neo4jUtil.isAvailable()) {
            return emptySubgraph();
        }
        String label = Neo4jUtil.kbLabel(knowledgeId);
        if (maxDepth <= 0) maxDepth = 2;
        if (maxNodes <= 0) maxNodes = 50;

        List<org.neo4j.driver.Record> records;
        if (documentId != null) {
            records = querySubgraphByDocument(label, String.valueOf(documentId), maxNodes);
        } else if (keyword != null && !keyword.isBlank()) {
            records = queryByKeyword(label, keyword, maxNodes);
        } else {
            records = querySampleSubgraph(label, maxNodes);
        }

        return buildSubgraphFromRecords(records);
    }

    private GraphSubgraphVO emptySubgraph() {
        GraphSubgraphVO empty = new GraphSubgraphVO();
        empty.setNodes(List.of());
        empty.setEdges(List.of());
        empty.setNodeCount(0);
        empty.setEdgeCount(0);
        return empty;
    }

    @Override
    public List<String> searchForRag(Long knowledgeId, String question, Long providerId) {
        if (!neo4jUtil.isAvailable()) {
            return Collections.emptyList();
        }
        String label = Neo4jUtil.kbLabel(knowledgeId);

        List<String> entityNames = graphExtractor.extractEntitiesFromQuestion(question, providerId);
        if (entityNames.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> results = new ArrayList<>();
        for (String entityName : entityNames) {
            String cypher = """
                MATCH (n:Entity:`%s` {name: $name})
                WHERE n.graph_source = 'merged' OR n.graph_source IS NULL
                OPTIONAL MATCH (n)-[r1]-(m1:Entity:`%s`)
                WHERE r1 IS NOT NULL AND (r1.graph_source = 'merged' OR r1.graph_source IS NULL)
                WITH n, r1, m1
                WHERE r1 IS NOT NULL
                OPTIONAL MATCH (m1)-[r2]-(m2:Entity:`%s`)
                WHERE m2 <> n AND m2 IS NOT NULL AND (r2.graph_source = 'merged' OR r2.graph_source IS NULL)
                RETURN n.name AS h1, type(r1) AS r1Type, r1.relation_type AS r1Rel, m1.name AS t1,
                       m1.name AS h2, type(r2) AS r2Type, r2.relation_type AS r2Rel, m2.name AS t2
                """.formatted(label, label, label);

            List<org.neo4j.driver.Record> records = neo4jUtil.query(cypher, Map.of("name", entityName));
            for (org.neo4j.driver.Record record : records) {
                String r1Rel = record.get("r1Rel").isNull() ? null : record.get("r1Rel").asString();
                String h1 = record.get("h1").isNull() ? null : record.get("h1").asString();
                String t1 = record.get("t1").isNull() ? null : record.get("t1").asString();
                if (r1Rel != null && h1 != null && t1 != null) {
                    results.add("%s %s %s".formatted(h1, r1Rel, t1));
                }

                String r2Rel = record.get("r2Rel").isNull() ? null : record.get("r2Rel").asString();
                String h2 = record.get("h2").isNull() ? null : record.get("h2").asString();
                String t2 = record.get("t2").isNull() ? null : record.get("t2").asString();
                if (r2Rel != null && h2 != null && t2 != null) {
                    results.add("%s %s %s".formatted(h2, r2Rel, t2));
                }
            }
        }

        return results.stream().distinct().limit(10).collect(Collectors.toList());
    }

    // ==================== 统计 ====================

    @Override
    public GraphStatsVO getStats(Long knowledgeId, Long documentId) {
        permissionHelper.checkMember(knowledgeId);
        if (!neo4jUtil.isAvailable()) {
            return new GraphStatsVO(0, 0, Map.of());
        }
        String label = Neo4jUtil.kbLabel(knowledgeId);
        GraphStatsVO stats;
        if (documentId != null) {
            stats = getStatsFromNeo4jForDoc(label, String.valueOf(documentId));
            // 检查该文档是否有正在运行的图谱抽取任务
            KnowledgeGraph kg = getKnowledgeGraph(knowledgeId);
            if (kg != null) {
                long runningCount = graphDocumentMapper.selectCount(
                        new LambdaQueryWrapper<GraphDocument>()
                                .eq(GraphDocument::getGraphId, kg.getId())
                                .eq(GraphDocument::getDocumentId, documentId)
                                .in(GraphDocument::getStatus, List.of(GraphTaskStatus.PENDING, GraphTaskStatus.RUNNING)));
                stats.setHasRunningTask(runningCount > 0);
            }
        } else {
            stats = getStatsFromNeo4j(label);
            // 知识库级别：检查 KnowledgeGraph 整体状态
            KnowledgeGraph kg = getKnowledgeGraph(knowledgeId);
            if (kg != null) {
                stats.setHasRunningTask(kg.getStatus() == GraphTaskStatus.PENDING || kg.getStatus() == GraphTaskStatus.RUNNING);
            }
        }
        return stats;
    }

    // ==================== 批量检查 ====================

    @Override
    public List<Long> getExistingDocIds(Long knowledgeId, List<Long> documentIds) {
        permissionHelper.checkMember(knowledgeId);
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        KnowledgeGraph kg = getKnowledgeGraph(knowledgeId);
        if (kg == null) {
            return List.of();
        }
        // 查询状态为 SUCCESS 且有实体的 GraphDocument 记录
        List<GraphDocument> existing = graphDocumentMapper.selectList(
                new LambdaQueryWrapper<GraphDocument>()
                        .eq(GraphDocument::getGraphId, kg.getId())
                        .in(GraphDocument::getDocumentId, documentIds)
                        .eq(GraphDocument::getStatus, GraphTaskStatus.COMPLETED)
                        .gt(GraphDocument::getEntityCount, 0));
        return existing.stream()
                .map(GraphDocument::getDocumentId)
                .collect(Collectors.toList());
    }

    // ==================== 删除 ====================

    @Override
    public void deleteByKnowledgeId(Long knowledgeId) {
        permissionHelper.checkPermission(knowledgeId, KnowledgeRole.MANAGER);
        checkNeo4jAvailable();
        String label = Neo4jUtil.kbLabel(knowledgeId);
        String cypher = "MATCH (n:Entity:`%s`) DETACH DELETE n".formatted(label);
        neo4jUtil.run(cypher, Map.of());

        // 重置知识库图谱统计
        Knowledge knowledge = knowledgeService.getById(knowledgeId);
        if (knowledge != null) {
            knowledge.setNodeCount(0);
            knowledge.setEdgeCount(0);
            knowledgeService.updateById(knowledge);
        }

        // 重置 KnowledgeGraph 状态
        KnowledgeGraph kg = getKnowledgeGraph(knowledgeId);
        if (kg != null) {
            kg.setNodeCount(0);
            kg.setEdgeCount(0);
            kg.setStatus(GraphTaskStatus.PENDING);
            kg.setTaskId(null);
            kg.setErrorMessage(null);
            knowledgeGraphMapper.updateById(kg);
            // 删除关联的 GraphDocument 记录
            graphDocumentMapper.delete(new LambdaQueryWrapper<GraphDocument>()
                    .eq(GraphDocument::getGraphId, kg.getId()));
        }

        log.info("[图谱] 已清空知识库图谱: knowledgeId={}", knowledgeId);
    }

    @Override
    public void deleteByDocumentId(Long knowledgeId, Long documentId) {
        permissionHelper.checkPermission(knowledgeId, KnowledgeRole.DEVELOPER);
        checkNeo4jAvailable();
        String label = Neo4jUtil.kbLabel(knowledgeId);
        String cypher = "MATCH (n:Entity:`%s` {document_id: $docId}) WHERE n.graph_source = 'single_doc' DETACH DELETE n".formatted(label);
        neo4jUtil.run(cypher, Map.of("docId", String.valueOf(documentId)));
        updateKnowledgeGraphStats(knowledgeId, label);

        // 删除 GraphDocument 记录
        KnowledgeGraph kg = getKnowledgeGraph(knowledgeId);
        if (kg != null) {
            graphDocumentMapper.delete(new LambdaQueryWrapper<GraphDocument>()
                    .eq(GraphDocument::getGraphId, kg.getId())
                    .eq(GraphDocument::getDocumentId, documentId));
        }

        log.info("[图谱] 已删除文档关联图谱: knowledgeId={}, documentId={}", knowledgeId, documentId);
    }

    // ==================== 手动编辑 ====================

    @Override
    public GraphNodeVO createNode(Long knowledgeId, String name, String entityType, String description) {
        permissionHelper.checkPermission(knowledgeId, KnowledgeRole.DEVELOPER);
        checkNeo4jAvailable();
        String label = Neo4jUtil.kbLabel(knowledgeId);
        String nodeId = String.valueOf(snowflakeId());

        String cypher = """
            MERGE (n:Entity:`%s` {name: $name})
            ON CREATE SET n.id = $nodeId, n.entity_type = $entityType, n.description = $description,
                          n.source = 'manual', n.knowledge_id = $knowledgeId,
                          n.graph_source = 'merged',
                          n.created_at = datetime(), n.updated_at = datetime()
            ON MATCH SET n.entity_type = $entityType, n.description = $description,
                         n.updated_at = datetime()
            RETURN n
            """.formatted(label);

        List<org.neo4j.driver.Record> records = neo4jUtil.queryWrite(cypher, Map.of(
                "name", name, "nodeId", nodeId, "entityType", entityType,
                "description", description != null ? description : "",
                "knowledgeId", String.valueOf(knowledgeId)));

        updateKnowledgeGraphStats(knowledgeId, label);

        if (!records.isEmpty()) {
            return toNodeVO(records.get(0).get("n").asNode());
        }
        throw new BizException(ErrorCode.INTERNAL_ERROR);
    }

    @Override
    public GraphEdgeVO createEdge(Long knowledgeId, String headName, String relationType, String tailName, String description) {
        permissionHelper.checkPermission(knowledgeId, KnowledgeRole.DEVELOPER);
        checkNeo4jAvailable();
        String label = Neo4jUtil.kbLabel(knowledgeId);
        String edgeId = String.valueOf(snowflakeId());

        String cypher = """
            MATCH (h:Entity:`%s` {name: $headName})
            MATCH (t:Entity:`%s` {name: $tailName})
            MERGE (h)-[r:RELATION {relation_type: $relationType}]->(t)
            ON CREATE SET r.id = $edgeId, r.description = $description, r.weight = 1.0,
                          r.source = 'manual', r.knowledge_id = $knowledgeId,
                          r.graph_source = 'merged',
                          r.created_at = datetime(), r.updated_at = datetime()
            ON MATCH SET r.description = $description, r.updated_at = datetime()
            RETURN r, elementId(h) AS startId, elementId(t) AS endId
            """.formatted(label, label);

        List<org.neo4j.driver.Record> records = neo4jUtil.queryWrite(cypher, Map.of(
                "headName", headName, "tailName", tailName, "relationType", relationType,
                "edgeId", edgeId, "description", description != null ? description : ""));

        updateKnowledgeGraphStats(knowledgeId, label);

        if (!records.isEmpty()) {
            org.neo4j.driver.Record record = records.get(0);
            GraphEdgeVO vo = toEdgeVO(record.get("r").asRelationship());
            vo.setStartNodeElementId(record.get("startId").asString());
            vo.setEndNodeElementId(record.get("endId").asString());
            return vo;
        }
        throw new BizException(ErrorCode.GRAPH_NODE_NOT_FOUND);
    }

    @Override
    public void deleteNode(Long knowledgeId, String elementId) {
        permissionHelper.checkPermission(knowledgeId, KnowledgeRole.DEVELOPER);
        checkNeo4jAvailable();
        String label = Neo4jUtil.kbLabel(knowledgeId);
        String cypher = "MATCH (n:Entity:`%s`) WHERE elementId(n) = $elementId DETACH DELETE n".formatted(label);
        neo4jUtil.run(cypher, Map.of("elementId", elementId));
        updateKnowledgeGraphStats(knowledgeId, label);
    }

    @Override
    public void deleteEdge(Long knowledgeId, String elementId) {
        permissionHelper.checkPermission(knowledgeId, KnowledgeRole.DEVELOPER);
        checkNeo4jAvailable();
        String label = Neo4jUtil.kbLabel(knowledgeId);
        String cypher = "MATCH ()-[r:RELATION]->() WHERE elementId(r) = $elementId DELETE r".formatted(label);
        neo4jUtil.run(cypher, Map.of("elementId", elementId));
        updateKnowledgeGraphStats(knowledgeId, label);
    }

    // ==================== 内部方法 ====================

    /**
     * 获取或创建知识库的 KnowledgeGraph 记录
     */
    private KnowledgeGraph getOrCreateKnowledgeGraph(Long knowledgeId) {
        KnowledgeGraph kg = getKnowledgeGraph(knowledgeId);
        if (kg != null) {
            return kg;
        }
        kg = new KnowledgeGraph();
        kg.setKnowledgeId(knowledgeId);
        kg.setStatus(GraphTaskStatus.PENDING);
        kg.setNodeCount(0);
        kg.setEdgeCount(0);
        knowledgeGraphMapper.insert(kg);
        return kg;
    }

    /**
     * 获取知识库的 KnowledgeGraph 记录
     */
    private KnowledgeGraph getKnowledgeGraph(Long knowledgeId) {
        return knowledgeGraphMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeGraph>()
                        .eq(KnowledgeGraph::getKnowledgeId, knowledgeId));
    }

    /**
     * 过滤掉已有运行中任务的文档，返回仍需处理的文档ID列表
     */
    private List<Long> filterRunningDocs(Long graphId, List<Long> documentIds) {
        List<GraphDocument> running = graphDocumentMapper.selectList(
                new LambdaQueryWrapper<GraphDocument>()
                        .eq(GraphDocument::getGraphId, graphId)
                        .in(GraphDocument::getDocumentId, documentIds)
                        .in(GraphDocument::getStatus, List.of(GraphTaskStatus.PENDING, GraphTaskStatus.RUNNING)));
        Set<Long> runningDocIds = running.stream()
                .map(GraphDocument::getDocumentId)
                .collect(Collectors.toSet());
        return documentIds.stream()
                .filter(id -> !runningDocIds.contains(id))
                .collect(Collectors.toList());
    }

    /**
     * 将三元组列表写入 Neo4j（MERGE 幂等）
     *
     * @return int[]{nodeCount, edgeCount}
     */
    int[] writeTriplesToNeo4j(String label, List<GraphTripleDTO> triples, Long documentId, Long knowledgeId, String source) {
        Set<String> nodes = new HashSet<>();
        int edgeCount = 0;

        for (GraphTripleDTO triple : triples) {
            if (triple.getHead() == null || triple.getTail() == null || triple.getRelation() == null) {
                continue;
            }

            String nodeCypher = """
                MERGE (n:Entity:`%s` {name: $name})
                ON CREATE SET n.id = $nodeId, n.entity_type = $entityType, n.description = $description,
                              n.source = $source, n.knowledge_id = $knowledgeId,
                              n.graph_source = 'merged',
                              n.created_at = datetime(), n.updated_at = datetime()
                ON MATCH SET n.updated_at = datetime()
                """.formatted(label);

            if (documentId != null) {
                nodeCypher = nodeCypher.replace("n.source = $source,",
                        "n.source = $source, n.document_id = $documentId,");
            }

            neo4jUtil.run(nodeCypher, Map.of(
                    "name", triple.getHead(), "nodeId", String.valueOf(snowflakeId()),
                    "entityType", triple.getHeadType() != null ? triple.getHeadType() : "其他",
                    "description", triple.getHeadDesc() != null ? triple.getHeadDesc() : "",
                    "source", source, "knowledgeId", String.valueOf(knowledgeId),
                    "documentId", documentId != null ? String.valueOf(documentId) : ""));
            nodes.add(triple.getHead());

            neo4jUtil.run(nodeCypher.replace("$name", "$name2").replace("$nodeId", "$nodeId2")
                            .replace("$entityType", "$entityType2").replace("$description", "$description2"),
                    Map.of("name2", triple.getTail(), "nodeId2", String.valueOf(snowflakeId()),
                            "entityType2", triple.getTailType() != null ? triple.getTailType() : "其他",
                            "description2", triple.getTailDesc() != null ? triple.getTailDesc() : "",
                            "source", source, "knowledgeId", String.valueOf(knowledgeId),
                            "documentId", documentId != null ? String.valueOf(documentId) : ""));
            nodes.add(triple.getTail());

            String relCypher = """
                MATCH (h:Entity:`%s` {name: $head})
                MATCH (t:Entity:`%s` {name: $tail})
                MERGE (h)-[r:RELATION {relation_type: $relation}]->(t)
                ON CREATE SET r.id = $relId, r.description = $relDesc, r.weight = 1.0,
                              r.source = $source, r.knowledge_id = $knowledgeId,
                              r.graph_source = 'merged',
                              r.created_at = datetime(), r.updated_at = datetime()
                ON MATCH SET r.weight = r.weight + 0.1, r.updated_at = datetime()
                """.formatted(label, label);

            Map<String, Object> relParams = new LinkedHashMap<>();
            relParams.put("head", triple.getHead());
            relParams.put("tail", triple.getTail());
            relParams.put("relation", triple.getRelation());
            relParams.put("relId", String.valueOf(snowflakeId()));
            relParams.put("relDesc", triple.getRelationDesc() != null ? triple.getRelationDesc() : "");
            relParams.put("source", source);
            relParams.put("knowledgeId", String.valueOf(knowledgeId));

            neo4jUtil.run(relCypher, relParams);
            edgeCount++;
        }

        return new int[]{nodes.size(), edgeCount};
    }

    private List<org.neo4j.driver.Record> queryByKeyword(String label, String keyword, int maxNodes) {
        String cypher = """
            MATCH (n:Entity:`%s`)
            WHERE toLower(n.name) CONTAINS toLower($keyword)
              AND (n.graph_source = 'merged' OR n.graph_source IS NULL)
            WITH n LIMIT $maxNodes
            OPTIONAL MATCH (n)-[r]-(m:Entity:`%s`)
            WHERE r.graph_source = 'merged' OR r.graph_source IS NULL
            RETURN n, r, m
            """.formatted(label, label);

        return neo4jUtil.query(cypher, Map.of("keyword", keyword, "maxNodes", maxNodes));
    }

    private List<org.neo4j.driver.Record> querySampleSubgraph(String label, int maxNodes) {
        String cypher = """
            MATCH (n:Entity:`%s`)-[r:RELATION]-(m:Entity:`%s`)
            WHERE n.graph_source = 'merged' OR n.graph_source IS NULL
            RETURN n, r, m
            LIMIT $maxNodes
            """.formatted(label, label);

        return neo4jUtil.query(cypher, Map.of("maxNodes", maxNodes));
    }

    private List<org.neo4j.driver.Record> querySubgraphByDocument(String label, String documentId, int maxNodes) {
        String cypher = """
            MATCH (n:Entity:`%s`)-[r:RELATION]-(m:Entity:`%s`)
            WHERE n.document_id = $docId AND n.graph_source = 'single_doc'
            RETURN n, r, m
            LIMIT $maxNodes
            """.formatted(label, label);

        return neo4jUtil.query(cypher, Map.of("docId", documentId, "maxNodes", maxNodes));
    }

    private GraphStatsVO getStatsFromNeo4jForDoc(String label, String documentId) {
        String cypher = """
            MATCH (n:Entity:`%s`)
            WHERE n.document_id = $docId AND n.graph_source = 'single_doc'
            WITH count(n) AS nodeCount
            OPTIONAL MATCH (a:Entity:`%s`)-[r:RELATION]-(b:Entity:`%s`)
            WHERE a.document_id = $docId AND a.graph_source = 'single_doc'
            RETURN nodeCount, count(DISTINCT r) AS edgeCount
            """.formatted(label, label, label);

        List<org.neo4j.driver.Record> records = neo4jUtil.query(cypher, Map.of("docId", documentId));
        int nodeCount = 0, edgeCount = 0;
        if (!records.isEmpty()) {
            org.neo4j.driver.Record r = records.get(0);
            nodeCount = r.get("nodeCount").asInt();
            edgeCount = r.get("edgeCount").asInt();
        }
        return new GraphStatsVO(nodeCount, edgeCount, Map.of());
    }

    private GraphSubgraphVO buildSubgraphFromRecords(List<org.neo4j.driver.Record> records) {
        Map<String, GraphNodeVO> nodeMap = new LinkedHashMap<>();
        Map<String, GraphEdgeVO> edgeMap = new LinkedHashMap<>();

        for (org.neo4j.driver.Record record : records) {
            if (!record.get("n").isNull()) {
                Node n = record.get("n").asNode();
                nodeMap.putIfAbsent(n.elementId(), toNodeVO(n));
            }
            if (!record.get("m").isNull()) {
                Node m = record.get("m").asNode();
                nodeMap.putIfAbsent(m.elementId(), toNodeVO(m));
            }
            if (!record.get("r").isNull()) {
                Relationship r = record.get("r").asRelationship();
                edgeMap.putIfAbsent(r.elementId(), toEdgeVO(r));
            }
        }

        GraphSubgraphVO vo = new GraphSubgraphVO();
        vo.setNodes(new ArrayList<>(nodeMap.values()));
        vo.setEdges(new ArrayList<>(edgeMap.values()));
        vo.setNodeCount(nodeMap.size());
        vo.setEdgeCount(edgeMap.size());
        return vo;
    }

    private GraphNodeVO toNodeVO(Node node) {
        GraphNodeVO vo = new GraphNodeVO();
        vo.setElementId(node.elementId());
        vo.setId(node.containsKey("id") ? node.get("id").asString() : null);
        vo.setName(node.containsKey("name") ? node.get("name").asString() : null);
        vo.setEntityType(node.containsKey("entity_type") ? node.get("entity_type").asString() : null);
        vo.setDescription(node.containsKey("description") ? node.get("description").asString() : null);
        vo.setSource(node.containsKey("source") ? node.get("source").asString() : null);
        vo.setDocumentId(node.containsKey("document_id") ? Long.parseLong(node.get("document_id").asString()) : null);
        List<String> labels = new ArrayList<>();
        node.labels().forEach(label -> labels.add(label.toString()));
        vo.setLabels(labels);
        return vo;
    }

    private GraphEdgeVO toEdgeVO(Relationship rel) {
        GraphEdgeVO vo = new GraphEdgeVO();
        vo.setElementId(rel.elementId());
        vo.setId(rel.containsKey("id") ? rel.get("id").asString() : null);
        vo.setRelationType(rel.containsKey("relation_type") ? rel.get("relation_type").asString() : rel.type());
        vo.setDescription(rel.containsKey("description") ? rel.get("description").asString() : null);
        vo.setWeight(rel.containsKey("weight") ? rel.get("weight").asDouble() : 1.0);
        vo.setSource(rel.containsKey("source") ? rel.get("source").asString() : null);
        vo.setStartNodeElementId(rel.startNodeElementId());
        vo.setEndNodeElementId(rel.endNodeElementId());
        return vo;
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

        List<org.neo4j.driver.Record> records = neo4jUtil.query(cypher, Map.of());
        int nodeCount = 0, edgeCount = 0;
        if (!records.isEmpty()) {
            org.neo4j.driver.Record r = records.get(0);
            nodeCount = r.get("nodeCount").asInt();
            edgeCount = r.get("edgeCount").asInt();
        }

        String distCypher = """
            MATCH (n:Entity:`%s`)
            WHERE n.graph_source = 'merged' OR n.graph_source IS NULL
            RETURN n.entity_type AS type, count(*) AS cnt
            ORDER BY cnt DESC
            """.formatted(label);

        Map<String, Integer> typeDist = new LinkedHashMap<>();
        List<org.neo4j.driver.Record> distRecords = neo4jUtil.query(distCypher, Map.of());
        for (org.neo4j.driver.Record r : distRecords) {
            String type = r.get("type").isNull() ? "其他" : r.get("type").asString();
            typeDist.put(type, r.get("cnt").asInt());
        }

        return new GraphStatsVO(nodeCount, edgeCount, typeDist);
    }

    private void updateKnowledgeGraphStats(Long knowledgeId, String label) {
        GraphStatsVO stats = getStatsFromNeo4j(label);
        Knowledge knowledge = knowledgeService.getById(knowledgeId);
        if (knowledge != null) {
            knowledge.setNodeCount(stats.getNodeCount());
            knowledge.setEdgeCount(stats.getEdgeCount());
            knowledgeService.updateById(knowledge);
        }

        // 同步更新 KnowledgeGraph 记录
        KnowledgeGraph kg = getKnowledgeGraph(knowledgeId);
        if (kg != null) {
            kg.setNodeCount(stats.getNodeCount());
            kg.setEdgeCount(stats.getEdgeCount());
            knowledgeGraphMapper.updateById(kg);
        }
    }

    private long snowflakeId() {
        return java.util.concurrent.ThreadLocalRandom.current().nextLong(1000000000000000000L, Long.MAX_VALUE);
    }
}

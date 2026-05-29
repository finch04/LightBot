package com.lightbot.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.*;
import com.lightbot.entity.Knowledge;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.GraphTaskSource;
import com.lightbot.enums.GraphTaskStatus;
import com.lightbot.entity.GraphExtractionTask;
import com.lightbot.mapper.GraphExtractionTaskMapper;
import com.lightbot.model.graph.GraphExtractor;
import com.lightbot.enums.KnowledgeRole;
import com.lightbot.service.GraphService;
import com.lightbot.service.KnowledgePermissionHelper;
import com.lightbot.service.KnowledgeService;
import com.lightbot.service.TaskService;
import com.lightbot.util.Neo4jUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
    private final GraphExtractionTaskMapper graphExtractionTaskMapper;
    private final ObjectMapper objectMapper;

    /**
     * 检查 Neo4j 可用性，不可用时抛出业务异常
     */
    private void checkNeo4jAvailable() {
        if (!neo4jUtil.isAvailable()) {
            throw new BizException(ErrorCode.GRAPH_NEO4J_UNAVAILABLE);
        }
    }

    // ==================== 抽取 ====================

    @Override
    public Long extractFromDocument(Long knowledgeId, Long documentId, Long providerId) {
        checkNeo4jAvailable();
        // 权限校验：需要DEVELOPER及以上权限
        permissionHelper.checkPermission(knowledgeId, KnowledgeRole.DEVELOPER);

        // 1. 校验知识库
        Knowledge knowledge = knowledgeService.getById(knowledgeId);
        if (knowledge == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NOT_FOUND);
        }

        // 2. 创建抽取任务记录
        GraphExtractionTask task = new GraphExtractionTask();
        task.setKnowledgeId(knowledgeId);
        task.setDocumentId(documentId);
        task.setStatus(GraphTaskStatus.PENDING);
        task.setSource(documentId != null ? GraphTaskSource.AUTO : GraphTaskSource.AUTO);
        task.setEntityCount(0);
        task.setRelationCount(0);
        graphExtractionTaskMapper.insert(task);

        // 3. 构建异步任务 payload
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("graphTaskId", task.getId());
            payload.put("knowledgeId", knowledgeId);
            if (documentId != null) {
                payload.put("documentId", documentId);
            }
            if (providerId != null) {
                payload.put("providerId", providerId);
            }
            taskService.createTask(com.lightbot.enums.TaskType.GRAPH_EXTRACTION,
                    "图谱抽取", knowledge.getUserId(), knowledgeId,
                    objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }

        log.info("[图谱] 抽取任务已提交: knowledgeId={}, documentId={}, taskId={}", knowledgeId, documentId, task.getId());
        return task.getId();
    }

    // ==================== 导入 ====================

    @Override
    public GraphStatsVO importTriples(Long knowledgeId, List<GraphTripleDTO> triples, Long providerId) {
        checkNeo4jAvailable();
        // 权限校验：需要DEVELOPER及以上权限
        permissionHelper.checkPermission(knowledgeId, KnowledgeRole.DEVELOPER);

        Knowledge knowledge = knowledgeService.getById(knowledgeId);
        if (knowledge == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NOT_FOUND);
        }

        String label = Neo4jUtil.kbLabel(knowledgeId);
        int[] counts = writeTriplesToNeo4j(label, triples, null, knowledgeId, "import");

        // 更新统计
        updateKnowledgeGraphStats(knowledgeId, label);

        log.info("[图谱] 导入完成: knowledgeId={}, nodes={}, edges={}", knowledgeId, counts[0], counts[1]);
        return new GraphStatsVO(counts[0], counts[1], Map.of());
    }

    // ==================== 查询 ====================

    @Override
    public GraphSubgraphVO getSubgraph(Long knowledgeId, String keyword, int maxDepth, int maxNodes) {
        // 权限校验：需要成员权限
        permissionHelper.checkMember(knowledgeId);
        if (!neo4jUtil.isAvailable()) {
            GraphSubgraphVO empty = new GraphSubgraphVO();
            empty.setNodes(List.of());
            empty.setEdges(List.of());
            empty.setNodeCount(0);
            empty.setEdgeCount(0);
            return empty;
        }
        String label = Neo4jUtil.kbLabel(knowledgeId);
        if (maxDepth <= 0) maxDepth = 2;
        if (maxNodes <= 0) maxNodes = 50;

        List<org.neo4j.driver.Record> records;
        if (keyword != null && !keyword.isBlank()) {
            records = queryByKeyword(label, keyword, maxNodes);
        } else {
            records = querySampleSubgraph(label, maxNodes);
        }

        return buildSubgraphFromRecords(records);
    }

    @Override
    public List<String> searchForRag(Long knowledgeId, String question, Long providerId) {
        if (!neo4jUtil.isAvailable()) {
            return Collections.emptyList();
        }
        String label = Neo4jUtil.kbLabel(knowledgeId);

        // 1. 从问题中提取实体名
        List<String> entityNames = graphExtractor.extractEntitiesFromQuestion(question, providerId);
        if (entityNames.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 对每个实体查找 1-2 跳子图
        List<String> results = new ArrayList<>();
        for (String entityName : entityNames) {
            String cypher = """
                MATCH (n:Entity:`%s` {name: $name})
                OPTIONAL MATCH (n)-[r1]-(m1:Entity:`%s`)
                WITH n, r1, m1
                WHERE r1 IS NOT NULL
                OPTIONAL MATCH (m1)-[r2]-(m2:Entity:`%s`)
                WHERE m2 <> n AND m2 IS NOT NULL
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

        // 3. 去重 + 限制条数
        return results.stream().distinct().limit(10).collect(Collectors.toList());
    }

    // ==================== 统计 ====================

    @Override
    public GraphStatsVO getStats(Long knowledgeId) {
        // 权限校验：需要成员权限
        permissionHelper.checkMember(knowledgeId);
        if (!neo4jUtil.isAvailable()) {
            return new GraphStatsVO(0, 0, Map.of());
        }
        String label = Neo4jUtil.kbLabel(knowledgeId);
        return getStatsFromNeo4j(label);
    }

    // ==================== 删除 ====================

    @Override
    public void deleteByKnowledgeId(Long knowledgeId) {
        // 权限校验：需要MANAGER及以上权限
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
        log.info("[图谱] 已清空知识库图谱: knowledgeId={}", knowledgeId);
    }

    @Override
    public void deleteByDocumentId(Long knowledgeId, Long documentId) {
        checkNeo4jAvailable();
        String label = Neo4jUtil.kbLabel(knowledgeId);
        String cypher = "MATCH (n:Entity:`%s` {document_id: $docId}) DETACH DELETE n".formatted(label);
        neo4jUtil.run(cypher, Map.of("docId", String.valueOf(documentId)));
        updateKnowledgeGraphStats(knowledgeId, label);
        log.info("[图谱] 已删除文档关联图谱: knowledgeId={}, documentId={}", knowledgeId, documentId);
    }

    // ==================== 手动编辑 ====================

    @Override
    public GraphNodeVO createNode(Long knowledgeId, String name, String entityType, String description) {
        // 权限校验：需要DEVELOPER及以上权限
        permissionHelper.checkPermission(knowledgeId, KnowledgeRole.DEVELOPER);
        checkNeo4jAvailable();
        String label = Neo4jUtil.kbLabel(knowledgeId);
        String nodeId = String.valueOf(snowflakeId());

        String cypher = """
            MERGE (n:Entity:`%s` {name: $name})
            ON CREATE SET n.id = $nodeId, n.entity_type = $entityType, n.description = $description,
                          n.source = 'manual', n.knowledge_id = $knowledgeId,
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
        // 权限校验：需要DEVELOPER及以上权限
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
        // 权限校验：需要DEVELOPER及以上权限
        permissionHelper.checkPermission(knowledgeId, KnowledgeRole.DEVELOPER);
        checkNeo4jAvailable();
        String label = Neo4jUtil.kbLabel(knowledgeId);
        String cypher = "MATCH (n:Entity:`%s`) WHERE elementId(n) = $elementId DETACH DELETE n".formatted(label);
        neo4jUtil.run(cypher, Map.of("elementId", elementId));
        updateKnowledgeGraphStats(knowledgeId, label);
    }

    @Override
    public void deleteEdge(Long knowledgeId, String elementId) {
        // 权限校验：需要DEVELOPER及以上权限
        permissionHelper.checkPermission(knowledgeId, KnowledgeRole.DEVELOPER);
        checkNeo4jAvailable();
        String label = Neo4jUtil.kbLabel(knowledgeId);
        String cypher = "MATCH ()-[r:RELATION]->() WHERE elementId(r) = $elementId DELETE r".formatted(label);
        neo4jUtil.run(cypher, Map.of("elementId", elementId));
        updateKnowledgeGraphStats(knowledgeId, label);
    }

    // ==================== 内部方法 ====================

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

            // MERGE 两个节点
            String nodeCypher = """
                MERGE (n:Entity:`%s` {name: $name})
                ON CREATE SET n.id = $nodeId, n.entity_type = $entityType, n.description = $description,
                              n.source = $source, n.knowledge_id = $knowledgeId,
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

            // MERGE 关系
            String relCypher = """
                MATCH (h:Entity:`%s` {name: $head})
                MATCH (t:Entity:`%s` {name: $tail})
                MERGE (h)-[r:RELATION {relation_type: $relation}]->(t)
                ON CREATE SET r.id = $relId, r.description = $relDesc, r.weight = 1.0,
                              r.source = $source, r.knowledge_id = $knowledgeId,
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

    /**
     * 按关键词搜索节点及邻居
     */
    private List<org.neo4j.driver.Record> queryByKeyword(String label, String keyword, int maxNodes) {
        String cypher = """
            MATCH (n:Entity:`%s`)
            WHERE toLower(n.name) CONTAINS toLower($keyword)
            WITH n LIMIT $maxNodes
            OPTIONAL MATCH (n)-[r]-(m:Entity:`%s`)
            RETURN n, r, m
            """.formatted(label, label);

        return neo4jUtil.query(cypher, Map.of("keyword", keyword, "maxNodes", maxNodes));
    }

    /**
     * 采样高连接度节点及邻居（用于初始展示）
     */
    private List<org.neo4j.driver.Record> querySampleSubgraph(String label, int maxNodes) {
        String cypher = """
            MATCH (seed:Entity:`%s`)-[r]-(neighbor:Entity:`%s`)
            WITH seed, count(r) AS degree, collect(r) AS rels, collect(neighbor) AS neighbors
            ORDER BY degree DESC
            LIMIT 5
            UNWIND range(0, size(rels)-1) AS idx
            WITH seed, rels[idx] AS r, neighbors[idx] AS neighbor
            RETURN seed AS n, r, neighbor AS m
            LIMIT $maxNodes
            """.formatted(label, label);

        return neo4jUtil.query(cypher, Map.of("maxNodes", maxNodes));
    }

    /**
     * 从查询结果构建子图 VO
     */
    private GraphSubgraphVO buildSubgraphFromRecords(List<org.neo4j.driver.Record> records) {
        Map<String, GraphNodeVO> nodeMap = new LinkedHashMap<>();
        Map<String, GraphEdgeVO> edgeMap = new LinkedHashMap<>();

        for (org.neo4j.driver.Record record : records) {
            // 处理节点 n
            if (!record.get("n").isNull()) {
                Node n = record.get("n").asNode();
                nodeMap.putIfAbsent(n.elementId(), toNodeVO(n));
            }
            // 处理节点 m
            if (!record.get("m").isNull()) {
                Node m = record.get("m").asNode();
                nodeMap.putIfAbsent(m.elementId(), toNodeVO(m));
            }
            // 处理关系 r
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

    /**
     * 从 Neo4j 获取图谱统计
     */
    private GraphStatsVO getStatsFromNeo4j(String label) {
        String cypher = """
            MATCH (n:Entity:`%s`)
            WITH count(n) AS nodeCount
            OPTIONAL MATCH (:Entity:`%s`)-[r:RELATION]->(:Entity:`%s`)
            RETURN nodeCount, count(r) AS edgeCount
            """.formatted(label, label, label);

        List<org.neo4j.driver.Record> records = neo4jUtil.query(cypher, Map.of());
        int nodeCount = 0, edgeCount = 0;
        if (!records.isEmpty()) {
            org.neo4j.driver.Record r = records.get(0);
            nodeCount = r.get("nodeCount").asInt();
            edgeCount = r.get("edgeCount").asInt();
        }

        // 实体类型分布
        String distCypher = """
            MATCH (n:Entity:`%s`)
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

    /**
     * 更新知识库的图谱统计字段
     */
    private void updateKnowledgeGraphStats(Long knowledgeId, String label) {
        GraphStatsVO stats = getStatsFromNeo4j(label);
        Knowledge knowledge = knowledgeService.getById(knowledgeId);
        if (knowledge != null) {
            knowledge.setNodeCount(stats.getNodeCount());
            knowledge.setEdgeCount(stats.getEdgeCount());
            knowledgeService.updateById(knowledge);
        }
    }

    /**
     * 雪花算法 ID 生成（简化版，用于 Neo4j 节点/关系的业务 ID）
     */
    private long snowflakeId() {
        return java.util.concurrent.ThreadLocalRandom.current().nextLong(1000000000000000000L, Long.MAX_VALUE);
    }
}

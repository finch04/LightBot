package com.lightbot.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.*;
import com.lightbot.enums.ErrorCode;
import com.lightbot.model.ModelFactory;
import com.lightbot.service.StandaloneGraphService;
import com.lightbot.util.Neo4jUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 独立知识图谱服务实现（全局，不关联知识库）
 *
 * @author finch
 * @since 2026-05-29
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StandaloneGraphServiceImpl implements StandaloneGraphService {

    private final Neo4jUtil neo4jUtil;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;

    private static final long MAX_JSONL_SIZE = 5 * 1024 * 1024L; // 5MB
    private static final String VECTOR_INDEX_NAME = "standalone_entity_embedding";
    private volatile boolean vectorIndexEnsured = false;

    // ==================== 导入 ====================

    @Override
    public GraphStatsVO importFromJsonl(MultipartFile file, Long providerId) {
        // 1. 文件格式校验
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".jsonl")) {
            throw new BizException(ErrorCode.GRAPH_JSONL_INVALID_TYPE);
        }

        // 2. 文件大小校验
        if (file.getSize() > MAX_JSONL_SIZE) {
            throw new BizException(ErrorCode.GRAPH_JSONL_TOO_LARGE);
        }

        // 2. 逐行解析 JSONL
        List<GraphTripleDTO> triples = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty()) continue;

                Map<String, Object> raw;
                try {
                    raw = objectMapper.readValue(line, new TypeReference<>() {});
                } catch (Exception e) {
                    throw new BizException(ErrorCode.GRAPH_JSONL_INVALID_FORMAT);
                }

                GraphTripleDTO dto = new GraphTripleDTO();
                dto.setHead(getString(raw, "head"));
                dto.setHeadType(getString(raw, "headType"));
                dto.setHeadDesc(getString(raw, "headDesc"));
                dto.setRelation(getString(raw, "relation"));
                dto.setRelationDesc(getString(raw, "relationDesc"));
                dto.setTail(getString(raw, "tail"));
                dto.setTailType(getString(raw, "tailType"));
                dto.setTailDesc(getString(raw, "tailDesc"));

                if (dto.getHead() == null || dto.getRelation() == null || dto.getTail() == null) {
                    throw new BizException(ErrorCode.GRAPH_JSONL_INVALID_FORMAT);
                }
                triples.add(dto);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.GRAPH_JSONL_INVALID_FORMAT);
        }

        if (triples.isEmpty()) {
            throw new BizException(ErrorCode.GRAPH_JSONL_INVALID_FORMAT);
        }

        log.info("[独立图谱] JSONL解析完成, 三元组数={}", triples.size());
        return importTriples(triples, providerId);
    }

    @Override
    public GraphStatsVO importTriples(List<GraphTripleDTO> triples, Long providerId) {
        checkNeo4jAvailable();

        // 1. 收集所有唯一实体名，批量生成 embedding
        Set<String> entityNames = new LinkedHashSet<>();
        for (GraphTripleDTO t : triples) {
            if (t.getHead() != null) entityNames.add(t.getHead());
            if (t.getTail() != null) entityNames.add(t.getTail());
        }

        Map<String, float[]> embeddingMap = generateEmbeddings(new ArrayList<>(entityNames));

        // 2. 确保向量索引存在
        ensureVectorIndex(embeddingMap.values().stream().findFirst().map(e -> e.length).orElse(1536));

        // 3. 写入 Neo4j
        int[] counts = writeTriplesToNeo4j(triples, embeddingMap);

        log.info("[独立图谱] 导入完成, nodes={}, edges={}", counts[0], counts[1]);
        return new GraphStatsVO(counts[0], counts[1], Map.of());
    }

    // ==================== 查询 ====================

    @Override
    public GraphSubgraphVO getSubgraph(String keyword, int maxDepth, int maxNodes) {
        if (!neo4jUtil.isAvailable()) {
            return emptySubgraph();
        }
        if (maxDepth <= 0) maxDepth = 2;
        if (maxNodes <= 0) maxNodes = 50;

        String label = Neo4jUtil.STANDALONE_LABEL;
        List<org.neo4j.driver.Record> records;
        if (keyword != null && !keyword.isBlank()) {
            records = queryByKeyword(label, keyword, maxNodes);
        } else {
            records = querySampleSubgraph(label, maxNodes);
        }

        return buildSubgraphFromRecords(records);
    }

    @Override
    public List<GraphNodeVO> semanticSearch(String query, int topK, Long providerId) {
        checkNeo4jAvailable();

        // 1. 生成查询向量
        float[] queryEmbedding = embedText(query);
        if (queryEmbedding == null) {
            return Collections.emptyList();
        }

        // 2. 向量检索
        try {
            String cypher = """
                CALL db.index.vector.queryNodes($indexName, $topK, $queryEmbedding)
                YIELD node, score
                WHERE 'standalone' IN labels(node)
                RETURN node, score
                ORDER BY score DESC
                """;

            List<org.neo4j.driver.Record> records = neo4jUtil.query(cypher, Map.of(
                    "indexName", VECTOR_INDEX_NAME,
                    "topK", topK,
                    "queryEmbedding", queryEmbedding));

            List<GraphNodeVO> results = new ArrayList<>();
            for (org.neo4j.driver.Record r : records) {
                Node node = r.get("node").asNode();
                GraphNodeVO vo = toNodeVO(node);
                vo.setScore(r.get("score").asDouble());
                results.add(vo);
            }
            return results;
        } catch (Exception e) {
            log.warn("[独立图谱] 向量搜索失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ==================== 统计 ====================

    @Override
    public GraphStatsVO getStats() {
        boolean available = neo4jUtil.isAvailable();
        if (!available) {
            GraphStatsVO vo = new GraphStatsVO(0, 0, Map.of());
            vo.setAvailable(false);
            return vo;
        }
        GraphStatsVO vo = getStatsFromNeo4j(Neo4jUtil.STANDALONE_LABEL);
        vo.setAvailable(true);
        return vo;
    }

    // ==================== 删除 ====================

    @Override
    public void deleteAll() {
        checkNeo4jAvailable();
        String cypher = "MATCH (n:Entity:standalone) DETACH DELETE n";
        neo4jUtil.run(cypher, Map.of());
        log.info("[独立图谱] 已清空全部图谱数据");
    }

    // ==================== 节点 CRUD ====================

    @Override
    public GraphNodeVO createNode(String name, String entityType, String description, Long providerId) {
        checkNeo4jAvailable();
        String label = Neo4jUtil.STANDALONE_LABEL;
        String nodeId = String.valueOf(snowflakeId());

        // 生成 embedding
        float[] embedding = embedText(name);

        String cypher = """
            MERGE (n:Entity:`%s` {name: $name})
            ON CREATE SET n.id = $nodeId, n.entity_type = $entityType, n.description = $description,
                          n.source = 'manual', n.created_at = datetime(), n.updated_at = datetime()
            ON MATCH SET n.entity_type = $entityType, n.description = $description,
                         n.updated_at = datetime()
            RETURN n
            """.formatted(label);

        List<org.neo4j.driver.Record> records = neo4jUtil.queryWrite(cypher, Map.of(
                "name", name, "nodeId", nodeId, "entityType", entityType,
                "description", description != null ? description : ""));

        // 写入 embedding
        if (embedding != null && !records.isEmpty()) {
            writeNodeEmbedding(name, embedding);
        }

        if (!records.isEmpty()) {
            return toNodeVO(records.get(0).get("n").asNode());
        }
        throw new BizException(ErrorCode.INTERNAL_ERROR);
    }

    @Override
    public GraphNodeVO updateNode(String elementId, String name, String entityType, String description) {
        checkNeo4jAvailable();
        String label = Neo4jUtil.STANDALONE_LABEL;

        // 先查询原节点，判断 name 是否变更
        String oldName = getNodeName(elementId);

        String cypher = """
            MATCH (n:Entity:`%s`) WHERE elementId(n) = $elementId
            SET n.name = $name, n.entity_type = $entityType, n.description = $description, n.updated_at = datetime()
            RETURN n
            """.formatted(label);

        List<org.neo4j.driver.Record> records = neo4jUtil.queryWrite(cypher, Map.of(
                "elementId", elementId, "name", name,
                "entityType", entityType != null ? entityType : "其他",
                "description", description != null ? description : ""));

        // name 变更时重新生成 embedding
        if (!name.equals(oldName)) {
            float[] embedding = embedText(name);
            if (embedding != null) {
                writeNodeEmbedding(name, embedding);
            }
        }

        if (!records.isEmpty()) {
            return toNodeVO(records.get(0).get("n").asNode());
        }
        throw new BizException(ErrorCode.GRAPH_NODE_NOT_FOUND);
    }

    @Override
    public void deleteNode(String elementId) {
        checkNeo4jAvailable();
        String cypher = "MATCH (n:Entity:standalone) WHERE elementId(n) = $elementId DETACH DELETE n";
        neo4jUtil.run(cypher, Map.of("elementId", elementId));
    }

    // ==================== 边 CRUD ====================

    @Override
    public GraphEdgeVO createEdge(String headName, String relationType, String tailName, String description) {
        checkNeo4jAvailable();
        String label = Neo4jUtil.STANDALONE_LABEL;
        String edgeId = String.valueOf(snowflakeId());

        String cypher = """
            MATCH (h:Entity:`%s` {name: $headName})
            MATCH (t:Entity:`%s` {name: $tailName})
            MERGE (h)-[r:RELATION {relation_type: $relationType}]->(t)
            ON CREATE SET r.id = $edgeId, r.description = $description, r.weight = 1.0,
                          r.source = 'manual', r.created_at = datetime(), r.updated_at = datetime()
            ON MATCH SET r.description = $description, r.updated_at = datetime()
            RETURN r, elementId(h) AS startId, elementId(t) AS endId
            """.formatted(label, label);

        List<org.neo4j.driver.Record> records = neo4jUtil.queryWrite(cypher, Map.of(
                "headName", headName, "tailName", tailName, "relationType", relationType,
                "edgeId", edgeId, "description", description != null ? description : ""));

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
    public GraphEdgeVO updateEdge(String elementId, String relationType, String description, Double weight) {
        checkNeo4jAvailable();

        StringBuilder setClauses = new StringBuilder();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("elementId", elementId);

        if (relationType != null) {
            setClauses.append("r.relation_type = $relationType, ");
            params.put("relationType", relationType);
        }
        if (description != null) {
            setClauses.append("r.description = $description, ");
            params.put("description", description);
        }
        if (weight != null) {
            setClauses.append("r.weight = $weight, ");
            params.put("weight", weight);
        }
        setClauses.append("r.updated_at = datetime()");

        String cypher = """
            MATCH ()-[r:RELATION]->() WHERE elementId(r) = $elementId
            SET %s
            RETURN r
            """.formatted(setClauses);

        List<org.neo4j.driver.Record> records = neo4jUtil.queryWrite(cypher, params);
        if (!records.isEmpty()) {
            return toEdgeVO(records.get(0).get("r").asRelationship());
        }
        throw new BizException(ErrorCode.GRAPH_EDGE_NOT_FOUND);
    }

    @Override
    public void deleteEdge(String elementId) {
        checkNeo4jAvailable();
        String cypher = "MATCH ()-[r:RELATION]->() WHERE elementId(r) = $elementId DELETE r";
        neo4jUtil.run(cypher, Map.of("elementId", elementId));
    }

    @Override
    public List<String> listNodeNames() {
        if (!neo4jUtil.isAvailable()) {
            return List.of();
        }
        String cypher = "MATCH (n:Entity:standalone) RETURN n.name AS name ORDER BY name";
        List<org.neo4j.driver.Record> records = neo4jUtil.query(cypher, Map.of());
        return records.stream()
                .filter(r -> !r.get("name").isNull())
                .map(r -> r.get("name").asString())
                .toList();
    }

    // ==================== 内部方法 ====================

    private void checkNeo4jAvailable() {
        if (!neo4jUtil.isAvailable()) {
            throw new BizException(ErrorCode.GRAPH_NEO4J_UNAVAILABLE);
        }
    }

    private GraphSubgraphVO emptySubgraph() {
        GraphSubgraphVO vo = new GraphSubgraphVO();
        vo.setNodes(List.of());
        vo.setEdges(List.of());
        vo.setNodeCount(0);
        vo.setEdgeCount(0);
        return vo;
    }

    /**
     * 批量生成实体名 embedding
     */
    private Map<String, float[]> generateEmbeddings(List<String> entityNames) {
        if (entityNames.isEmpty()) return Map.of();

        Map<String, float[]> result = new LinkedHashMap<>();
        int batchSize = 100;

        for (int i = 0; i < entityNames.size(); i += batchSize) {
            int end = Math.min(i + batchSize, entityNames.size());
            List<String> batch = entityNames.subList(i, end);
            try {
                EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(batch, null));
                for (int j = 0; j < batch.size(); j++) {
                    result.put(batch.get(j), response.getResults().get(j).getOutput());
                }
            } catch (Exception e) {
                log.warn("[独立图谱] 批量embedding生成失败: {}", e.getMessage());
            }
        }
        return result;
    }

    /**
     * 生成单条文本的 embedding
     */
    private float[] embedText(String text) {
        try {
            EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(List.of(text), null));
            return response.getResult().getOutput();
        } catch (Exception e) {
            log.warn("[独立图谱] embedding生成失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 确保向量索引存在（懒创建）
     */
    private void ensureVectorIndex(int dimensions) {
        if (vectorIndexEnsured) return;
        synchronized (this) {
            if (vectorIndexEnsured) return;
            try {
                String cypher = """
                    CREATE VECTOR INDEX %s IF NOT EXISTS
                    FOR (n:Entity:standalone) ON (n.embedding)
                    OPTIONS {indexConfig: {`vector.dimensions`: %d, `vector.similarity_function`: 'cosine'}}
                    """.formatted(VECTOR_INDEX_NAME, dimensions);
                neo4jUtil.run(cypher, Map.of());
                vectorIndexEnsured = true;
                log.info("[独立图谱] 向量索引已确保, dimensions={}", dimensions);
            } catch (Exception e) {
                log.warn("[独立图谱] 向量索引创建失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 写入节点的 embedding 属性
     */
    private void writeNodeEmbedding(String name, float[] embedding) {
        String cypher = """
            MATCH (n:Entity:standalone {name: $name})
            SET n.embedding = $embedding
            """;
        neo4jUtil.run(cypher, Map.of("name", name, "embedding", embedding));
    }

    /**
     * 获取节点当前名称
     */
    private String getNodeName(String elementId) {
        String cypher = "MATCH (n:Entity:standalone) WHERE elementId(n) = $elementId RETURN n.name AS name";
        List<org.neo4j.driver.Record> records = neo4jUtil.query(cypher, Map.of("elementId", elementId));
        if (!records.isEmpty() && !records.get(0).get("name").isNull()) {
            return records.get(0).get("name").asString();
        }
        return null;
    }

    /**
     * 将三元组写入 Neo4j（MERGE 幂等）
     */
    private int[] writeTriplesToNeo4j(List<GraphTripleDTO> triples, Map<String, float[]> embeddingMap) {
        String label = Neo4jUtil.STANDALONE_LABEL;
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
                              n.source = 'import', n.created_at = datetime(), n.updated_at = datetime()
                ON MATCH SET n.updated_at = datetime()
                """.formatted(label);

            neo4jUtil.run(nodeCypher, Map.of(
                    "name", triple.getHead(), "nodeId", String.valueOf(snowflakeId()),
                    "entityType", triple.getHeadType() != null ? triple.getHeadType() : "其他",
                    "description", triple.getHeadDesc() != null ? triple.getHeadDesc() : ""));
            nodes.add(triple.getHead());

            neo4jUtil.run(nodeCypher.replace("$name", "$name2").replace("$nodeId", "$nodeId2")
                            .replace("$entityType", "$entityType2").replace("$description", "$description2"),
                    Map.of("name2", triple.getTail(), "nodeId2", String.valueOf(snowflakeId()),
                            "entityType2", triple.getTailType() != null ? triple.getTailType() : "其他",
                            "description2", triple.getTailDesc() != null ? triple.getTailDesc() : ""));
            nodes.add(triple.getTail());

            // MERGE 关系
            String relCypher = """
                MATCH (h:Entity:`%s` {name: $head})
                MATCH (t:Entity:`%s` {name: $tail})
                MERGE (h)-[r:RELATION {relation_type: $relation}]->(t)
                ON CREATE SET r.id = $relId, r.description = $relDesc, r.weight = 1.0,
                              r.source = 'import', r.created_at = datetime(), r.updated_at = datetime()
                ON MATCH SET r.weight = r.weight + 0.1, r.updated_at = datetime()
                """.formatted(label, label);

            neo4jUtil.run(relCypher, Map.of(
                    "head", triple.getHead(), "tail", triple.getTail(), "relation", triple.getRelation(),
                    "relId", String.valueOf(snowflakeId()),
                    "relDesc", triple.getRelationDesc() != null ? triple.getRelationDesc() : ""));
            edgeCount++;
        }

        // 批量写入 embedding
        if (embeddingMap != null) {
            for (String entityName : nodes) {
                float[] emb = embeddingMap.get(entityName);
                if (emb != null) {
                    writeNodeEmbedding(entityName, emb);
                }
            }
        }

        return new int[]{nodes.size(), edgeCount};
    }

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

    private List<org.neo4j.driver.Record> querySampleSubgraph(String label, int maxNodes) {
        // 1. 有边的节点：取度最高的种子及其邻居
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
        List<org.neo4j.driver.Record> records = neo4jUtil.query(cypher, Map.of("maxNodes", maxNodes));

        // 2. 孤立节点（无边）：补充到结果中
        String isolatedCypher = """
            MATCH (n:Entity:`%s`)
            WHERE NOT (n)-[]-()
            RETURN n, null AS r, null AS m
            LIMIT $remain
            """.formatted(label);
        int remain = maxNodes - records.size();
        if (remain > 0) {
            records.addAll(neo4jUtil.query(isolatedCypher, Map.of("remain", remain)));
        }

        return records;
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

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString().trim() : null;
    }

    private long snowflakeId() {
        return ThreadLocalRandom.current().nextLong(1000000000000000000L, Long.MAX_VALUE);
    }
}

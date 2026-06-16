package com.lightbot.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 图检索工具类
 * <p>基于 Milvus Entity/Triple 向量检索 + Neo4j 图遍历 + PPR 排序</p>
 * <p>实现流程：种子实体检索 → 2-hop 子图查询 → PPR 迭代 → 结果排序</p>
 *
 * @author finch
 * @since 2026-06-16
 */
@Slf4j
@Component
public class GraphRetrievalUtil {

    private static final int PPR_ITERATIONS = 15;

    private final MilvusUtil milvusUtil;
    private final Neo4jUtil neo4jUtil;

    public GraphRetrievalUtil(MilvusUtil milvusUtil, Neo4jUtil neo4jUtil) {
        this.milvusUtil = milvusUtil;
        this.neo4jUtil = neo4jUtil;
    }

    /**
     * 图检索是否可用（Milvus + Neo4j 均可用）
     */
    public boolean isAvailable() {
        return milvusUtil.isAvailable() && neo4jUtil.isAvailable();
    }

    /**
     * 图检索主入口
     *
     * @param knowledgeId    知识库ID
     * @param queryVector    查询向量
     * @param graphEntityTopK Milvus Entity 检索 topK
     * @param graphTripleTopK Milvus Triple 检索 topK
     * @param graphMaxNodes   PPR 子图最大节点数
     * @param graphTopK       最终返回结果数
     * @param pprDamping      PPR 阻尼系数
     * @return 图检索结果（entity_descriptions + triple_descriptions + score）
     */
    public List<Map<String, Object>> search(Long knowledgeId, float[] queryVector,
                                             int graphEntityTopK, int graphTripleTopK,
                                             int graphMaxNodes, int graphTopK,
                                             double pprDamping) {
        // 1. Milvus 向量检索种子实体和三元组
        List<Map<String, Object>> seedEntities = milvusUtil.searchEntities(knowledgeId, queryVector, graphEntityTopK);
        List<Map<String, Object>> seedTriples = milvusUtil.searchTriples(knowledgeId, queryVector, graphTripleTopK);

        if (seedEntities.isEmpty() && seedTriples.isEmpty()) {
            log.info("[GraphRetrieval] 无种子实体/三元组, knowledgeId={}", knowledgeId);
            return List.of();
        }

        // 2. 构建种子权重 Map（entityId → weight）
        Map<Long, Double> seedWeights = new LinkedHashMap<>();
        for (Map<String, Object> entity : seedEntities) {
            Long id = (Long) entity.get("id");
            double score = ((Number) entity.get("score")).doubleValue();
            seedWeights.put(id, score);
        }
        // Triple 的 source/target 也作为种子
        for (Map<String, Object> triple : seedTriples) {
            Long sourceId = (Long) triple.get("source_id");
            Long targetId = (Long) triple.get("target_id");
            double score = ((Number) triple.get("score")).doubleValue();
            seedWeights.merge(sourceId, score, Math::max);
            seedWeights.merge(targetId, score, Math::max);
        }

        // 3. Neo4j 查询 2-hop 子图
        String label = Neo4jUtil.kbLabel(knowledgeId);
        Map<Long, Set<Long>> adjacency = new LinkedHashMap<>();
        Map<Long, String> entityNames = new LinkedHashMap<>();
        Map<Long, String> entityDescriptions = new LinkedHashMap<>();

        querySubgraph(label, seedWeights.keySet(), graphMaxNodes, adjacency, entityNames, entityDescriptions);

        if (adjacency.isEmpty()) {
            log.info("[GraphRetrieval] 子图为空, knowledgeId={}", knowledgeId);
            return List.of();
        }

        // 4. PPR 迭代
        Map<Long, Double> pprScores = ppr(seedWeights, adjacency, pprDamping, PPR_ITERATIONS);

        // 5. Neo4j 查询三元组关系
        Set<Long> topEntityIds = pprScores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(graphTopK * 2)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Map<String, Object>> triples = queryTriples(label, topEntityIds);

        // 6. 构建结果
        return buildResults(pprScores, entityDescriptions, triples, graphTopK);
    }

    /**
     * 从 Neo4j 查询种子实体的 2-hop 子图
     * <p>收集邻接关系用于 PPR 迭代</p>
     */
    private void querySubgraph(String label, Set<Long> seedEntityIds, int maxNodes,
                                Map<Long, Set<Long>> adjacency,
                                Map<Long, String> entityNames,
                                Map<Long, String> entityDescriptions) {
        if (seedEntityIds.isEmpty()) {
            return;
        }

        // 将 seed IDs 转为字符串列表用于 Cypher 查询
        List<String> seedIdStrs = seedEntityIds.stream()
                .map(String::valueOf)
                .toList();

        // 2-hop Cypher 查询：从种子出发，沿 RELATION 边遍历最多 2 跳
        String cypher = """
            MATCH (n:Entity:`%s`)
            WHERE n.id IN $seedIds
            WITH collect(n) AS seeds
            UNWIND seeds AS seed
            MATCH path = (seed)-[*1..2]-(neighbor:Entity:`%s`)
            WHERE seed <> neighbor
            WITH nodes(path) AS pathNodes, relationships(path) AS pathRels
            UNWIND pathNodes AS node
            WITH DISTINCT node
            LIMIT $maxNodes
            OPTIONAL MATCH (node)-[r:RELATION]-(other:Entity:`%s`)
            RETURN node.id AS nodeId, node.name AS name, node.description AS description,
                   other.id AS otherId
            """.formatted(label, label, label);

        try {
            List<org.neo4j.driver.Record> records = neo4jUtil.query(cypher,
                    Map.of("seedIds", seedIdStrs, "maxNodes", maxNodes));

            for (org.neo4j.driver.Record record : records) {
                if (record.get("nodeId").isNull()) continue;

                long nodeId = Long.parseLong(record.get("nodeId").asString());
                String name = record.get("name").isNull() ? "" : record.get("name").asString();
                String desc = record.get("description").isNull() ? "" : record.get("description").asString();

                entityNames.putIfAbsent(nodeId, name);
                entityDescriptions.putIfAbsent(nodeId, desc);

                adjacency.computeIfAbsent(nodeId, k -> new LinkedHashSet<>());

                if (!record.get("otherId").isNull()) {
                    long otherId = Long.parseLong(record.get("otherId").asString());
                    adjacency.computeIfAbsent(nodeId, k -> new LinkedHashSet<>()).add(otherId);
                    adjacency.computeIfAbsent(otherId, k -> new LinkedHashSet<>()).add(nodeId);
                }
            }
        } catch (Exception e) {
            log.error("[GraphRetrieval] 子图查询异常: {}", e.getMessage(), e);
        }
    }

    /**
     * Personalized PageRank 迭代算法
     * <p>公式: score(v) = (1 - d) * seed(v) + d * Σ(score(u) / degree(u))</p>
     *
     * @param seedWeights 种子节点初始权重
     * @param adjacency   邻接表
     * @param damping     阻尼系数（建议 0.85）
     * @param iterations  迭代次数（建议 15）
     * @return 节点 PPR 分数
     */
    private Map<Long, Double> ppr(Map<Long, Double> seedWeights,
                                   Map<Long, Set<Long>> adjacency,
                                   double damping, int iterations) {
        // 初始化：所有节点分数为 0，种子节点有初始权重
        Map<Long, Double> scores = new LinkedHashMap<>();
        for (Long nodeId : adjacency.keySet()) {
            scores.put(nodeId, 0.0);
        }
        // 种子节点归一化
        double seedSum = seedWeights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (seedSum > 0) {
            for (Map.Entry<Long, Double> entry : seedWeights.entrySet()) {
                scores.put(entry.getKey(), entry.getValue() / seedSum);
            }
        }

        // 迭代
        for (int iter = 0; iter < iterations; iter++) {
            Map<Long, Double> newScores = new LinkedHashMap<>();
            for (Long nodeId : adjacency.keySet()) {
                // 随机跳转部分：回到种子节点
                double teleport = (1 - damping) * seedWeights.getOrDefault(nodeId, 0.0) / Math.max(seedSum, 1);

                // 邻居贡献部分
                double neighborContribution = 0;
                Set<Long> neighbors = adjacency.getOrDefault(nodeId, Set.of());
                for (Long neighbor : neighbors) {
                    int degree = adjacency.getOrDefault(neighbor, Set.of()).size();
                    if (degree > 0) {
                        neighborContribution += scores.getOrDefault(neighbor, 0.0) / degree;
                    }
                }

                newScores.put(nodeId, teleport + damping * neighborContribution);
            }
            scores = newScores;
        }

        return scores;
    }

    /**
     * 从 Neo4j 查询实体间的三元组关系
     */
    private List<Map<String, Object>> queryTriples(String label, Set<Long> entityIds) {
        if (entityIds.isEmpty()) {
            return List.of();
        }

        List<String> idStrs = entityIds.stream().map(String::valueOf).toList();

        String cypher = """
            MATCH (h:Entity:`%s`)-[r:RELATION]->(t:Entity:`%s`)
            WHERE h.id IN $entityIds AND t.id IN $entityIds
            RETURN h.id AS headId, h.name AS headName, h.description AS headDesc,
                   r.relation_type AS relationType, r.description AS relationDesc,
                   t.id AS tailId, t.name AS tailName, t.description AS tailDesc
            """.formatted(label, label);

        try {
            List<org.neo4j.driver.Record> records = neo4jUtil.query(cypher, Map.of("entityIds", idStrs));
            List<Map<String, Object>> results = new ArrayList<>(records.size());
            for (org.neo4j.driver.Record record : records) {
                Map<String, Object> triple = new LinkedHashMap<>();
                triple.put("head_id", Long.parseLong(record.get("headId").asString()));
                triple.put("head_name", record.get("headName").asString());
                triple.put("head_desc", record.get("headDesc").isNull() ? "" : record.get("headDesc").asString());
                triple.put("relation_type", record.get("relationType").asString());
                triple.put("relation_desc", record.get("relationDesc").isNull() ? "" : record.get("relationDesc").asString());
                triple.put("tail_id", Long.parseLong(record.get("tailId").asString()));
                triple.put("tail_name", record.get("tailName").asString());
                triple.put("tail_desc", record.get("tailDesc").isNull() ? "" : record.get("tailDesc").asString());
                results.add(triple);
            }
            return results;
        } catch (Exception e) {
            log.error("[GraphRetrieval] 三元组查询异常: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 构建图检索结果
     * <p>每个结果包含：entity_descriptions（实体描述拼接）、triple_descriptions（三元组描述拼接）、score（PPR 最高分）</p>
     */
    private List<Map<String, Object>> buildResults(Map<Long, Double> pprScores,
                                                    Map<Long, String> entityDescriptions,
                                                    List<Map<String, Object>> triples,
                                                    int graphTopK) {
        // 按 PPR 分数排序取 topK 实体
        List<Long> topEntities = pprScores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(graphTopK)
                .map(Map.Entry::getKey)
                .toList();

        Set<Long> topEntitySet = new LinkedHashSet<>(topEntities);

        // 收集 top 实体的描述
        List<String> entityDescList = new ArrayList<>();
        for (Long entityId : topEntities) {
            String desc = entityDescriptions.getOrDefault(entityId, "");
            if (!desc.isBlank()) {
                entityDescList.add(desc);
            }
        }

        // 收集涉及 top 实体的三元组描述
        List<String> tripleDescList = new ArrayList<>();
        double maxTripleScore = 0;
        for (Map<String, Object> triple : triples) {
            long headId = (long) triple.get("head_id");
            long tailId = (long) triple.get("tail_id");
            if (topEntitySet.contains(headId) && topEntitySet.contains(tailId)) {
                String headName = (String) triple.get("head_name");
                String relation = (String) triple.get("relation_type");
                String tailName = (String) triple.get("tail_name");
                String tripleDesc = headName + " " + relation + " " + tailName;
                tripleDescList.add(tripleDesc);

                // 三元组分数取两端实体 PPR 分数的平均值
                double headScore = pprScores.getOrDefault(headId, 0.0);
                double tailScore = pprScores.getOrDefault(tailId, 0.0);
                maxTripleScore = Math.max(maxTripleScore, (headScore + tailScore) / 2);
            }
        }

        // 组装结果
        List<Map<String, Object>> results = new ArrayList<>();

        if (!entityDescList.isEmpty()) {
            double maxEntityScore = topEntities.stream()
                    .mapToDouble(id -> pprScores.getOrDefault(id, 0.0))
                    .max().orElse(0);
            Map<String, Object> entityResult = new LinkedHashMap<>();
            entityResult.put("chunk_id", 0L);  // 图检索无 chunk_id，用 0 占位
            entityResult.put("content", String.join("\n\n", entityDescList));
            entityResult.put("document_id", 0L);
            entityResult.put("document_name", "图谱-实体");
            entityResult.put("score", maxEntityScore);
            results.add(entityResult);
        }

        if (!tripleDescList.isEmpty()) {
            Map<String, Object> tripleResult = new LinkedHashMap<>();
            tripleResult.put("chunk_id", 0L);
            tripleResult.put("content", String.join("\n", tripleDescList));
            tripleResult.put("document_id", 0L);
            tripleResult.put("document_name", "图谱-三元组");
            tripleResult.put("score", maxTripleScore);
            results.add(tripleResult);
        }

        return results;
    }
}

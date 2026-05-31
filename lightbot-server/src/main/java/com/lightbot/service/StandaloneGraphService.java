package com.lightbot.service;

import com.lightbot.dto.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 独立知识图谱服务接口（全局，不关联知识库）
 *
 * @author finch
 * @since 2026-05-29
 */
public interface StandaloneGraphService {

    /**
     * 从JSONL文件导入三元组
     *
     * @param file       JSONL文件
     * @param providerId 模型提供商ID（用于生成embedding）
     * @return 导入统计
     */
    GraphStatsVO importFromJsonl(MultipartFile file, Long providerId);

    /**
     * 手动批量导入三元组
     *
     * @param triples    三元组列表
     * @param providerId 模型提供商ID
     * @return 导入统计
     */
    GraphStatsVO importTriples(List<GraphTripleDTO> triples, Long providerId);

    /**
     * 获取子图（可视化用）
     *
     * @param keyword  搜索关键词（null表示采样高连接度节点）
     * @param maxDepth 最大跳数
     * @param maxNodes 最大节点数
     * @return 子图数据
     */
    GraphSubgraphVO getSubgraph(String keyword, int maxDepth, int maxNodes);

    /**
     * 语义搜索节点（向量检索）
     *
     * @param query      搜索文本
     * @param topK       返回数量
     * @param providerId 模型提供商ID
     * @return 匹配的节点列表
     */
    List<GraphNodeVO> semanticSearch(String query, int topK, Long providerId);

    /**
     * 获取图谱统计
     */
    GraphStatsVO getStats();

    /**
     * 清空独立图谱
     */
    void deleteAll();

    /**
     * 手动创建节点
     */
    GraphNodeVO createNode(String name, String entityType, String description, Long providerId);

    /**
     * 更新节点
     */
    GraphNodeVO updateNode(String elementId, String name, String entityType, String description);

    /**
     * 删除节点
     */
    void deleteNode(String elementId);

    /**
     * 手动创建边
     */
    GraphEdgeVO createEdge(String headName, String relationType, String tailName, String description);

    /**
     * 更新边
     */
    GraphEdgeVO updateEdge(String elementId, String relationType, String description, Double weight);

    /**
     * 删除边
     */
    void deleteEdge(String elementId);

    /**
     * 获取所有节点名称列表（用于关系创建时选择）
     */
    List<String> listNodeNames();

    /**
     * 重建向量索引（为缺失embedding的节点补生成向量）
     *
     * @return 补生成的节点数
     */
    int rebuildVectorIndex();
}

package com.lightbot.service;

import com.lightbot.dto.*;

import java.util.List;
import java.util.Map;

/**
 * 知识图谱服务接口
 *
 * @author finch
 * @since 2026-05-29
 */
public interface GraphService {

    /**
     * 触发文档的图谱抽取（异步任务）
     *
     * @param knowledgeId 知识库ID
     * @param request     抽取配置请求
     * @return 任务ID
     */
    Long extractFromDocument(Long knowledgeId, GraphExtractRequest request);

    /**
     * 自动触发图谱抽取（内部调用，跳过权限校验）
     *
     * @param knowledgeId 知识库ID
     * @param documentId  文档ID
     * @return 任务ID，null 表示跳过（Neo4j 不可用或知识库不存在）
     */
    Long autoExtractFromDocument(Long knowledgeId, Long documentId);

    /**
     * 批量导入三元组到图谱
     *
     * @param knowledgeId 知识库ID
     * @param triples     三元组列表
     * @param providerId  模型提供商ID（用于实体 Embedding）
     * @return 导入结果统计
     */
    GraphStatsVO importTriples(Long knowledgeId, List<GraphTripleDTO> triples, Long providerId);

    /**
     * 获取子图（用于可视化和检索）
     *
     * @param knowledgeId 知识库ID
     * @param documentId  文档ID（为null表示全库图谱）
     * @param keyword     搜索关键词（null 表示采样高连接度节点）
     * @param maxDepth    最大跳数（默认2）
     * @param maxNodes    最大节点数（默认50）
     * @return 子图数据
     */
    GraphSubgraphVO getSubgraph(Long knowledgeId, Long documentId, String keyword, int maxDepth, int maxNodes);

    /**
     * 图谱检索：从问题中提取实体，展开子图，返回文本格式的三元组
     *
     * @param knowledgeId 知识库ID
     * @param question    用户问题
     * @param providerId  模型提供商ID
     * @return 文本三元组列表，如 ["张三 担任 技术总监", ...]
     */
    List<String> searchForRag(Long knowledgeId, String question, Long providerId);

    /**
     * 获取图谱统计信息
     *
     * @param knowledgeId 知识库ID
     * @param documentId  文档ID（为null表示全库统计）
     * @return 统计信息
     */
    GraphStatsVO getStats(Long knowledgeId, Long documentId);

    /**
     * 批量查询哪些文档已有图谱数据
     *
     * @param knowledgeId 知识库ID
     * @param documentIds 文档ID列表
     * @return 已有图谱的文档ID集合
     */
    List<Long> getExistingDocIds(Long knowledgeId, List<Long> documentIds);

    /**
     * 删除知识库的全部图谱数据
     *
     * @param knowledgeId 知识库ID
     */
    void deleteByKnowledgeId(Long knowledgeId);

    /**
     * 删除文档关联的图谱数据
     *
     * @param knowledgeId 知识库ID
     * @param documentId  文档ID
     */
    void deleteByDocumentId(Long knowledgeId, Long documentId);

    /**
     * 手动创建节点
     *
     * @param knowledgeId 知识库ID
     * @param name        节点名称
     * @param entityType  实体类型
     * @param description 描述
     * @return 创建的节点
     */
    GraphNodeVO createNode(Long knowledgeId, String name, String entityType, String description);

    /**
     * 手动创建边
     *
     * @param knowledgeId 知识库ID
     * @param headName    起始节点名称
     * @param relationType 关系类型
     * @param tailName    目标节点名称
     * @param description 描述
     * @return 创建的边
     */
    GraphEdgeVO createEdge(Long knowledgeId, String headName, String relationType, String tailName, String description);

    /**
     * 删除节点（级联删除关联边）
     *
     * @param knowledgeId 知识库ID
     * @param elementId   节点 elementId
     */
    void deleteNode(Long knowledgeId, String elementId);

    /**
     * 删除边
     *
     * @param knowledgeId 知识库ID
     * @param elementId   边 elementId
     */
    void deleteEdge(Long knowledgeId, String elementId);
}

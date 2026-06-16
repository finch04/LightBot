package com.lightbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lightbot.entity.Embedding;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * 向量 Mapper
 * <p>pgvector 的 vector 类型和余弦距离运算符(&lt;=&gt;)无法通过 MyBatis-Plus 映射，
 * 因此向量相关操作使用 @Select/@Insert/@Delete 注解编写原生SQL</p>
 *
 * @author finch
 * @since 2026-05-19
 */
@Mapper
public interface EmbeddingMapper extends BaseMapper<Embedding> {

    /**
     * 存储向量（pgvector ::vector 类型转换需原生SQL）
     *
     * @param id        主键ID
     * @param chunkId   分块ID
     * @param modelName 模型名称
     * @param dimension 向量维度
     * @param vector    向量字符串 "[0.1,0.2,...]"
     */
    @Insert("INSERT INTO embedding (id, chunk_id, model_name, dimension, vector, create_time) " +
            "VALUES (#{id}, #{chunkId}, #{modelName}, #{dimension}, #{vector}::vector, NOW())")
    void insertVector(@Param("id") Long id, @Param("chunkId") Long chunkId,
                      @Param("modelName") String modelName, @Param("dimension") int dimension,
                      @Param("vector") String vector);

    /**
     * 余弦相似度检索 Top-K
     * <p>使用 pgvector 的 <=> 运算符计算余弦距离</p>
     *
     * @param vector      查询向量字符串
     * @param knowledgeId 知识库ID
     * @param topK        返回数量
     * @return 检索结果（chunk_id, content, document_name, score）
     */
    @Select("SELECT c.id AS chunk_id, c.content, c.knowledge_id, c.document_id, " +
            "d.name AS document_name, " +
            "1 - (e.vector <=> #{vector}::vector) AS score " +
            "FROM embedding e " +
            "JOIN chunk c ON e.chunk_id = c.id " +
            "JOIN document d ON c.document_id = d.id " +
            "WHERE c.knowledge_id = #{knowledgeId} AND d.deleted = 0 " +
            "ORDER BY e.vector <=> #{vector}::vector LIMIT #{topK}")
    List<Map<String, Object>> searchSimilar(@Param("vector") String vector,
                                             @Param("knowledgeId") Long knowledgeId,
                                             @Param("topK") int topK);

    /**
     * 余弦相似度检索 Top-K（带阈值过滤，下沉到SQL层）
     * <p>相比 searchSimilar，将阈值过滤从Java层下沉到SQL WHERE子句，
     * 避免传输低于阈值的无效数据</p>
     *
     * @param vector      查询向量字符串
     * @param knowledgeId 知识库ID
     * @param topK        返回数量
     * @param threshold   相似度阈值（0-1），低于此值的结果不返回
     * @return 检索结果（chunk_id, content, document_name, score）
     */
    @Select("SELECT c.id AS chunk_id, c.content, c.knowledge_id, c.document_id, " +
            "d.name AS document_name, " +
            "1 - (e.vector <=> #{vector}::vector) AS score " +
            "FROM embedding e " +
            "JOIN chunk c ON e.chunk_id = c.id " +
            "JOIN document d ON c.document_id = d.id " +
            "WHERE c.knowledge_id = #{knowledgeId} AND d.deleted = 0 " +
            "AND (1 - (e.vector <=> #{vector}::vector)) >= #{threshold} " +
            "ORDER BY e.vector <=> #{vector}::vector LIMIT #{topK}")
    List<Map<String, Object>> searchSimilarWithThreshold(@Param("vector") String vector,
                                                          @Param("knowledgeId") Long knowledgeId,
                                                          @Param("topK") int topK,
                                                          @Param("threshold") double threshold);

    /**
     * 批量存储向量
     *
     * @param vectors 向量数据列表，每项包含 [id, chunkId, modelName, dimension, vectorStr]
     */
    @Insert("<script>" +
            "INSERT INTO embedding (id, chunk_id, model_name, dimension, vector, create_time) VALUES " +
            "<foreach collection='vectors' item='v' separator=','>" +
            "(#{v.id}, #{v.chunkId}, #{v.modelName}, #{v.dimension}, #{v.vector}::vector, NOW())" +
            "</foreach>" +
            "</script>")
    void batchInsertVectors(@Param("vectors") List<Map<String, Object>> vectors);

    /**
     * 删除指定知识库的所有向量
     *
     * @param knowledgeId 知识库ID
     */
    @Delete("DELETE FROM embedding WHERE chunk_id IN (SELECT id FROM chunk WHERE knowledge_id = #{knowledgeId})")
    void deleteByKnowledgeId(@Param("knowledgeId") Long knowledgeId);

    /**
     * 删除指定文档的所有向量
     *
     * @param documentId 文档ID
     */
    @Delete("DELETE FROM embedding WHERE chunk_id IN (SELECT id FROM chunk WHERE document_id = #{documentId})")
    void deleteByDocumentId(@Param("documentId") Long documentId);

    /**
     * 全文检索（keyword 模式）
     * <p>使用 PostgreSQL tsvector + plainto_tsquery 进行全文检索</p>
     *
     * @param query       查询文本
     * @param knowledgeId 知识库ID
     * @param topK        返回数量
     * @return 检索结果（chunk_id, content, document_id, document_name, score）
     */
    @Select("SELECT c.id AS chunk_id, c.content, c.knowledge_id, c.document_id, " +
            "d.name AS document_name, " +
            "ts_rank(c.content_tsv, query) AS score " +
            "FROM chunk c, plainto_tsquery('simple', #{query}) query " +
            "JOIN document d ON c.document_id = d.id " +
            "WHERE c.knowledge_id = #{knowledgeId} AND d.deleted = 0 " +
            "AND c.content_tsv @@ query " +
            "ORDER BY score DESC LIMIT #{topK}")
    List<Map<String, Object>> searchByFullText(@Param("query") String query,
                                                @Param("knowledgeId") Long knowledgeId,
                                                @Param("topK") int topK);

    // ========== QA Pair 向量操作 ==========

    /**
     * 存储 QA Pair 向量
     *
     * @param id        主键ID
     * @param qaPairId  问答对ID
     * @param modelName 模型名称
     * @param dimension 向量维度
     * @param vector    向量字符串 "[0.1,0.2,...]"
     */
    @Insert("INSERT INTO embedding (id, qa_pair_id, model_name, dimension, vector, create_time) " +
            "VALUES (#{id}, #{qaPairId}, #{modelName}, #{dimension}, #{vector}::vector, NOW())")
    void insertQaPairVector(@Param("id") Long id, @Param("qaPairId") Long qaPairId,
                            @Param("modelName") String modelName, @Param("dimension") int dimension,
                            @Param("vector") String vector);

    /**
     * QA Pair 向量检索（带阈值过滤）
     *
     * @param vector      查询向量字符串
     * @param knowledgeId 知识库ID
     * @param topK        返回数量
     * @param threshold   相似度阈值
     * @return 检索结果（id, question, answer, knowledge_id, score）
     */
    @Select("SELECT qp.id, qp.question, qp.answer, qp.knowledge_id, " +
            "1 - (e.vector <=> #{vector}::vector) AS score " +
            "FROM embedding e " +
            "JOIN qa_pair qp ON e.qa_pair_id = qp.id " +
            "WHERE qp.knowledge_id = #{knowledgeId} AND qp.deleted = 0 AND qp.status = 'active' " +
            "AND (1 - (e.vector <=> #{vector}::vector)) >= #{threshold} " +
            "ORDER BY e.vector <=> #{vector}::vector LIMIT #{topK}")
    List<Map<String, Object>> searchSimilarQaPairs(@Param("vector") String vector,
                                                    @Param("knowledgeId") Long knowledgeId,
                                                    @Param("topK") int topK,
                                                    @Param("threshold") double threshold);

    /**
     * 删除指定问答对的向量
     *
     * @param qaPairId 问答对ID
     */
    @Delete("DELETE FROM embedding WHERE qa_pair_id = #{qaPairId}")
    void deleteByQaPairId(@Param("qaPairId") Long qaPairId);

    /**
     * 删除指定知识库所有问答对的向量
     *
     * @param knowledgeId 知识库ID
     */
    @Delete("DELETE FROM embedding WHERE qa_pair_id IN (SELECT id FROM qa_pair WHERE knowledge_id = #{knowledgeId})")
    void deleteByQaPairKnowledgeId(@Param("knowledgeId") Long knowledgeId);
}

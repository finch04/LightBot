package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.QaPairCreateDTO;
import com.lightbot.dto.QaPairSearchResultVO;
import com.lightbot.dto.QaPairUpdateDTO;
import com.lightbot.dto.QaPairVO;
import com.lightbot.entity.QaPair;
import com.lightbot.entity.Task;

import java.util.List;

/**
 * 问答对服务接口
 *
 * @author finch
 * @since 2026-05-29
 */
public interface QaPairService extends IService<QaPair> {

    /**
     * 创建问答对并触发向量化
     *
     * @param knowledgeId 知识库ID
     * @param dto         创建参数
     * @return 问答对VO
     */
    QaPairVO create(Long knowledgeId, QaPairCreateDTO dto);

    /**
     * 更新问答对（如果 question 变更，重新向量化）
     *
     * @param dto 更新参数
     * @return 问答对VO
     */
    QaPairVO update(QaPairUpdateDTO dto);

    /**
     * 分页查询问答对列表
     *
     * @param knowledgeId 知识库ID
     * @param pageNum     页码
     * @param pageSize    每页数量
     * @param keyword     搜索关键词
     * @return 分页结果
     */
    Page<QaPairVO> listByKnowledgeId(Long knowledgeId, int pageNum, int pageSize, String keyword);

    /**
     * 删除问答对（级联删除 embedding）
     *
     * @param id 问答对ID
     */
    void deleteById(Long id);

    /**
     * 批量导入问答对（带权限校验，供 Controller 调用）
     *
     * @param knowledgeId 知识库ID
     * @param items       问答对列表
     * @return 导入数量
     */
    int batchImport(Long knowledgeId, List<QaPairCreateDTO> items);

    /**
     * 批量导入问答对（内部调用，跳过权限校验，供任务执行器等非 web 上下文使用）
     *
     * @param knowledgeId 知识库ID
     * @param items       问答对列表
     * @return 导入数量
     */
    int batchImportInternal(Long knowledgeId, List<QaPairCreateDTO> items);

    /**
     * AI 生成问答对（异步任务）
     *
     * @param knowledgeId 知识库ID
     * @param count       生成条数
     * @param providerId  AI提供商ID（可选）
     * @param modelId     模型ID（可选）
     * @return 异步任务
     */
    Task generateByAI(Long knowledgeId, Integer count, Long providerId, String modelId);

    /**
     * 手动触发单个问答对向量化
     *
     * @param qaPairId 问答对ID
     */
    void vectorize(Long qaPairId);

    /**
     * 批量触发问答对向量化
     *
     * @param qaPairIds 问答对ID列表
     * @return 成功触发的数量
     */
    int batchVectorize(List<Long> qaPairIds);

    /**
     * 向量检索：在指定知识库中搜索最相关的问答对
     *
     * @param knowledgeId 知识库ID
     * @param queryVector 查询向量
     * @param topK        返回数量
     * @param threshold   相似度阈值
     * @return 检索结果
     */
    List<QaPairSearchResultVO> searchSimilar(Long knowledgeId, float[] queryVector, int topK, double threshold);
}

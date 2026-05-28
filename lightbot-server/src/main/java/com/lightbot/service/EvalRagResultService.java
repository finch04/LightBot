package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.entity.EvalRagResult;
import com.lightbot.entity.EvalRagResultDetail;

/**
 * RAG 评估结果服务接口
 *
 * @author finch
 * @since 2026-05-28
 */
public interface EvalRagResultService extends IService<EvalRagResult> {

    /**
     * 创建评估结果记录（不执行评估）
     */
    EvalRagResult createEvalResult(Long knowledgeId, Long benchmarkId);

    /**
     * 执行评估（同步，由任务执行器调用）
     */
    void executeEvaluation(Long resultId, Long benchmarkId, Long knowledgeId,
                           Long answerProviderId, String answerModelId,
                           Long judgeProviderId, String judgeModelId);

    /**
     * 查询知识库的评估历史
     */
    Page<EvalRagResult> listByKnowledgeId(Long knowledgeId, int pageNum, int pageSize);

    /**
     * 获取评估结果详情（含分页明细）
     */
    Page<EvalRagResultDetail> getResultDetail(Long resultId, int pageNum, int pageSize, boolean errorOnly);

    /**
     * 删除评估结果
     */
    void deleteResult(Long knowledgeId, Long resultId);
}

package com.lightbot.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.entity.EvalRagBenchmark;
import com.lightbot.entity.EvalRagBenchmarkItem;
import com.lightbot.entity.EvalRagResult;
import com.lightbot.entity.EvalRagResultDetail;
import com.lightbot.enums.ErrorCode;
import com.lightbot.mapper.EvalRagResultDetailMapper;
import com.lightbot.mapper.EvalRagResultMapper;
import com.lightbot.service.EvalRagBenchmarkService;
import com.lightbot.service.EvalRagResultService;
import com.lightbot.service.eval.RagEvaluationEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 评估结果服务实现
 *
 * @author finch
 * @since 2026-05-28
 */
@Slf4j
@Service
public class EvalRagResultServiceImpl
        extends ServiceImpl<EvalRagResultMapper, EvalRagResult>
        implements EvalRagResultService {

    @Autowired
    private EvalRagResultDetailMapper resultDetailMapper;
    @Autowired
    private EvalRagBenchmarkService benchmarkService;
    @Autowired
    private RagEvaluationEngine evaluationEngine;
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EvalRagResult createEvalResult(Long knowledgeId, Long benchmarkId) {
        // 1. 校验基准存在
        EvalRagBenchmark benchmark = benchmarkService.getById(benchmarkId);
        if (benchmark == null || !benchmark.getKnowledgeId().equals(knowledgeId)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        // 2. 创建评估结果记录
        EvalRagResult result = new EvalRagResult();
        result.setKnowledgeId(knowledgeId);
        result.setBenchmarkId(benchmark.getId());
        result.setBenchmarkName(benchmark.getName());
        result.setStatus("RUNNING");
        save(result);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void executeEvaluation(Long resultId, Long benchmarkId, Long knowledgeId,
                                   Long answerProviderId, String answerModelId,
                                   Long judgeProviderId, String judgeModelId) {
        EvalRagResult result = getById(resultId);
        if (result == null) return;

        long startTime = System.currentTimeMillis();
        try {
            // 1. 加载所有题目
            List<EvalRagBenchmarkItem> items = benchmarkService.listItemsByBenchmarkId(benchmarkId);
            if (items.isEmpty()) {
                result.setStatus("FAILED");
                result.setError("基准题目为空");
                result.setDurationMs(System.currentTimeMillis() - startTime);
                updateById(result);
                return;
            }

            // 2. 逐题评估
            for (int i = 0; i < items.size(); i++) {
                EvalRagBenchmarkItem item = items.get(i);
                try {
                    EvalRagResultDetail detail = evaluationEngine.evaluateQuestion(
                            item, knowledgeId,
                            answerProviderId, answerModelId,
                            judgeProviderId, judgeModelId);
                    detail.setResultId(resultId);
                    detail.setSortOrder(i);
                    resultDetailMapper.insert(detail);
                } catch (Exception e) {
                    log.error("[EvalRag] 评估题目失败 index={}, query={}: {}", i, item.getQuery(), e.getMessage());
                    EvalRagResultDetail errorDetail = new EvalRagResultDetail();
                    errorDetail.setResultId(resultId);
                    errorDetail.setQuery(item.getQuery());
                    errorDetail.setGoldChunkIds(item.getGoldChunkIds());
                    errorDetail.setGoldAnswer(item.getGoldAnswer());
                    errorDetail.setGeneratedAnswer("评估失败: " + e.getMessage());
                    errorDetail.setSortOrder(i);
                    resultDetailMapper.insert(errorDetail);
                }
            }

            // 3. 聚合指标
            List<EvalRagResultDetail> details = resultDetailMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EvalRagResultDetail>()
                            .eq(EvalRagResultDetail::getResultId, resultId)
                            .orderByAsc(EvalRagResultDetail::getSortOrder));

            Map<String, Object> metrics = evaluationEngine.aggregateMetrics(details);

            // 4. 更新结果
            result.setStatus("COMPLETED");
            result.setDurationMs(System.currentTimeMillis() - startTime);
            result.setOverallScore(((Number) metrics.getOrDefault("overallScore", 0.0)).doubleValue());
            try {
                if (metrics.containsKey("retrieval")) {
                    result.setRetrievalJson(objectMapper.writeValueAsString(metrics.get("retrieval")));
                }
                if (metrics.containsKey("answer")) {
                    result.setAnswerJson(objectMapper.writeValueAsString(metrics.get("answer")));
                }
                result.setConfigJson(objectMapper.writeValueAsString(Map.of(
                        "answerProviderId", answerProviderId != null ? String.valueOf(answerProviderId) : "null",
                        "answerModelId", answerModelId != null ? answerModelId : "",
                        "judgeProviderId", judgeProviderId != null ? String.valueOf(judgeProviderId) : "null",
                        "judgeModelId", judgeModelId != null ? judgeModelId : ""
                )));
            } catch (Exception ignored) {
            }
            updateById(result);

            log.info("[EvalRag] 评估完成: resultId={}, score={}, duration={}ms",
                    result.getId(), result.getOverallScore(), result.getDurationMs());

        } catch (Exception e) {
            log.error("[EvalRag] 评估失败: resultId={}, error={}", result.getId(), e.getMessage());
            result.setStatus("FAILED");
            result.setError(e.getMessage());
            result.setDurationMs(System.currentTimeMillis() - startTime);
            updateById(result);
        }
    }

    @Override
    public Page<EvalRagResult> listByKnowledgeId(Long knowledgeId, int pageNum, int pageSize) {
        Page<EvalRagResult> page = new Page<>(pageNum, pageSize);
        return page(page,
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EvalRagResult>()
                        .eq(EvalRagResult::getKnowledgeId, knowledgeId)
                        .orderByDesc(EvalRagResult::getCreateTime));
    }

    @Override
    public Page<EvalRagResultDetail> getResultDetail(Long resultId, int pageNum, int pageSize,
                                                       boolean errorOnly) {
        Page<EvalRagResultDetail> page = new Page<>(pageNum, pageSize);
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EvalRagResultDetail>()
                .eq(EvalRagResultDetail::getResultId, resultId)
                .orderByAsc(EvalRagResultDetail::getSortOrder);
        if (errorOnly) {
            wrapper.and(w -> w.isNull(EvalRagResultDetail::getAnswerScore)
                    .or().eq(EvalRagResultDetail::getAnswerScore, 0.0));
        }
        return resultDetailMapper.selectPage(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteResult(Long knowledgeId, Long resultId) {
        EvalRagResult result = getById(resultId);
        if (result == null || !result.getKnowledgeId().equals(knowledgeId)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        removeById(resultId);
        resultDetailMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EvalRagResultDetail>()
                        .eq(EvalRagResultDetail::getResultId, resultId));
    }
}

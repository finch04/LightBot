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
import com.lightbot.enums.KnowledgeRole;
import com.lightbot.mapper.EvalRagResultDetailMapper;
import com.lightbot.mapper.EvalRagResultMapper;
import com.lightbot.model.ModelFactory;
import com.lightbot.service.EvalRagBenchmarkService;
import com.lightbot.service.EvalRagResultService;
import com.lightbot.service.KnowledgeMemberService;
import com.lightbot.service.eval.RagEvaluationEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
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
    private ModelFactory modelFactory;
    @Autowired
    private KnowledgeMemberService permissionHelper;
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EvalRagResult createEvalResult(Long knowledgeId, Long benchmarkId) {
        // 0. 权限校验：需要DEVELOPER及以上权限
        permissionHelper.checkPermission(knowledgeId, KnowledgeRole.DEVELOPER);
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
                                   Long judgeProviderId, String judgeModelId,
                                   java.util.function.BiConsumer<Integer, String> progressCallback) {
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
                // 回调进度：10% ~ 80% 按题目数线性增长
                if (progressCallback != null) {
                    int progress = 10 + (int) (((i + 1) / (double) items.size()) * 70);
                    progressCallback.accept(progress, "正在评估 (" + (i + 1) + "/" + items.size() + ")...");
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
            // 5. AI 生成评估分析（优先使用评判模型，否则使用系统默认）
            try {
                Long analysisProviderId = judgeProviderId != null ? judgeProviderId : resolveAnalysisProviderId();
                String analysisModelId = judgeModelId != null ? judgeModelId : null;
                String analysis = generateAnalysis(result, details, metrics, analysisProviderId, analysisModelId);
                result.setAnalysis(analysis);
                updateById(result);
            } catch (Exception e) {
                log.warn("[EvalRag] AI评估分析生成失败: resultId={}, error={}", result.getId(), e.getMessage());
            }

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
        // 权限校验：需要成员权限
        permissionHelper.checkMember(knowledgeId);
        Page<EvalRagResult> page = new Page<>(pageNum, pageSize);
        return page(page,
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EvalRagResult>()
                        .eq(EvalRagResult::getKnowledgeId, knowledgeId)
                        .orderByDesc(EvalRagResult::getCreateTime));
    }

    @Override
    public Page<EvalRagResultDetail> getResultDetail(Long resultId, int pageNum, int pageSize,
                                                       boolean errorOnly) {
        // 权限校验：需要成员权限
        EvalRagResult result = getById(resultId);
        if (result != null) {
            permissionHelper.checkMember(result.getKnowledgeId());
        }
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
        // 权限校验：需要DEVELOPER及以上权限
        permissionHelper.checkPermission(knowledgeId, KnowledgeRole.DEVELOPER);
        EvalRagResult result = getById(resultId);
        if (result == null || !result.getKnowledgeId().equals(knowledgeId)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        removeById(resultId);
        resultDetailMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EvalRagResultDetail>()
                        .eq(EvalRagResultDetail::getResultId, resultId));
    }

    /**
     * 调用 AI 生成评估分析报告
     */
    private String generateAnalysis(EvalRagResult result, List<EvalRagResultDetail> details,
                                     Map<String, Object> metrics, Long providerId, String modelId) {
        // 1. 构建分析上下文
        StringBuilder context = new StringBuilder();
        context.append("## 评估概况\n");
        context.append("- 基准名称：").append(result.getBenchmarkName()).append("\n");
        context.append("- 综合评分：").append(String.format("%.1f%%", result.getOverallScore() * 100)).append("\n");
        context.append("- 题目总数：").append(details.size()).append("\n");
        context.append("- 耗时：").append(result.getDurationMs()).append("ms\n\n");

        // 2. 检索指标
        if (metrics.containsKey("retrieval")) {
            context.append("## 检索指标\n");
            @SuppressWarnings("unchecked")
            Map<String, Object> retrieval = (Map<String, Object>) metrics.get("retrieval");
            retrieval.forEach((k, v) -> context.append("- ").append(k).append(": ")
                    .append(v instanceof Number ? String.format("%.1f%%", ((Number) v).doubleValue() * 100) : v)
                    .append("\n"));
            context.append("\n");
        }

        // 3. 答案指标
        if (metrics.containsKey("answer")) {
            context.append("## 答案指标\n");
            @SuppressWarnings("unchecked")
            Map<String, Object> answer = (Map<String, Object>) metrics.get("answer");
            answer.forEach((k, v) -> context.append("- ").append(k).append(": ")
                    .append(v instanceof Number ? String.format("%.1f%%", ((Number) v).doubleValue() * 100) : v)
                    .append("\n"));
            context.append("\n");
        }

        // 4. 错误题目汇总
        long errorCount = details.stream().filter(d -> d.getAnswerScore() != null && d.getAnswerScore() < 1.0).count();
        context.append("## 错误统计\n");
        context.append("- 错误题目数：").append(errorCount).append("/").append(details.size()).append("\n");
        if (errorCount > 0) {
            context.append("- 错误题目列表：\n");
            details.stream()
                    .filter(d -> d.getAnswerScore() != null && d.getAnswerScore() < 1.0)
                    .limit(5)
                    .forEach(d -> context.append("  - 问题：").append(d.getQuery())
                            .append("，AI答案：").append(truncate(d.getGeneratedAnswer(), 50))
                            .append("，正确答案：").append(truncate(d.getGoldAnswer(), 50)).append("\n"));
        }

        // 5. 调用 LLM 生成分析
        String systemPrompt = "你是一个RAG系统评估分析专家。请根据评估结果数据，给出简洁的分析报告。\n"
                + "要求：\n"
                + "1. 总体评价（1-2句话）\n"
                + "2. 检索质量分析（如果检索指标偏低，指出可能原因）\n"
                + "3. 答案质量分析（如果答案准确率偏低，指出常见错误模式）\n"
                + "4. 改进建议（2-3条具体可操作的建议）\n"
                + "回复使用中文，控制在300字以内。";

        Map<String, Object> llmOptions = new HashMap<>();
        if (modelId != null && !modelId.isBlank()) {
            llmOptions.put("modelId", modelId);
        }
        ChatModel chatModel = modelFactory.getChatModel(providerId);
        ChatOptions options = modelFactory.buildChatOptions(providerId, llmOptions);

        ChatResponse response = com.lightbot.util.LlmTraceContext.callWithoutTrace(() ->
                chatModel.call(new Prompt(List.of(
                        new SystemMessage(systemPrompt),
                        new UserMessage(context.toString())), options)));
        return response.getResult().getOutput().getText().trim();
    }

    /**
     * 解析评估分析使用的providerId（复用评估配置中的judge模型）
     */
    private Long resolveAnalysisProviderId() {
        var ids = modelFactory.getAvailableProviderIds();
        if (ids.isEmpty()) {
            throw new BizException(ErrorCode.AI_NO_PROVIDER);
        }
        return ids.get(0);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}

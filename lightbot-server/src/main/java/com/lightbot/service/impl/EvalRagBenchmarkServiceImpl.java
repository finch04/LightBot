package com.lightbot.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.entity.EvalRagBenchmark;
import com.lightbot.entity.EvalRagBenchmarkItem;
import com.lightbot.enums.ErrorCode;
import com.lightbot.mapper.EvalRagBenchmarkItemMapper;
import com.lightbot.mapper.EvalRagBenchmarkMapper;
import com.lightbot.service.EvalRagBenchmarkService;
import com.lightbot.service.eval.RagEvaluationEngine;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * RAG 评估基准服务实现
 *
 * @author finch
 * @since 2026-05-28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalRagBenchmarkServiceImpl
        extends ServiceImpl<EvalRagBenchmarkMapper, EvalRagBenchmark>
        implements EvalRagBenchmarkService {

    private final EvalRagBenchmarkItemMapper benchmarkItemMapper;
    private final RagEvaluationEngine evaluationEngine;
    private final ObjectMapper objectMapper;

    @Override
    public List<EvalRagBenchmark> listByKnowledgeId(Long knowledgeId) {
        List<EvalRagBenchmark> benchmarks = list(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EvalRagBenchmark>()
                .eq(EvalRagBenchmark::getKnowledgeId, knowledgeId)
                .orderByDesc(EvalRagBenchmark::getCreateTime));
        // 计算每个基准是否包含 gold 数据
        for (EvalRagBenchmark bm : benchmarks) {
            List<EvalRagBenchmarkItem> sampleItems = benchmarkItemMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EvalRagBenchmarkItem>()
                            .eq(EvalRagBenchmarkItem::getBenchmarkId, bm.getId())
                            .last("LIMIT 5"));
            bm.setHasGoldChunks(sampleItems.stream().anyMatch(i -> i.getGoldChunkIds() != null && !"[]".equals(i.getGoldChunkIds())));
            bm.setHasGoldAnswer(sampleItems.stream().anyMatch(i -> i.getGoldAnswer() != null && !i.getGoldAnswer().isBlank()));
        }
        return benchmarks;
    }

    @Override
    public Page<EvalRagBenchmarkItem> getBenchmarkDetail(Long benchmarkId, int pageNum, int pageSize) {
        Page<EvalRagBenchmarkItem> page = new Page<>(pageNum, pageSize);
        return benchmarkItemMapper.selectPage(page,
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EvalRagBenchmarkItem>()
                        .eq(EvalRagBenchmarkItem::getBenchmarkId, benchmarkId)
                        .orderByAsc(EvalRagBenchmarkItem::getSortOrder));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EvalRagBenchmark createEmptyBenchmark(Long knowledgeId, String name, String description) {
        EvalRagBenchmark benchmark = new EvalRagBenchmark();
        benchmark.setKnowledgeId(knowledgeId);
        benchmark.setName(name);
        benchmark.setDescription(description);
        benchmark.setQuestionCount(0);
        benchmark.setStatus("generating");
        save(benchmark);
        return benchmark;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generateBenchmarkItems(Long benchmarkId, Long knowledgeId, Integer count,
                                        Long providerId, String modelId, Integer neighborCount) {
        // 1. AI 生成题目
        List<EvalRagBenchmarkItem> items = evaluationEngine.generateBenchmarkItems(
                knowledgeId, count, providerId, modelId, neighborCount != null ? neighborCount : 3);

        // 2. 保存题目
        for (int i = 0; i < items.size(); i++) {
            EvalRagBenchmarkItem item = items.get(i);
            item.setBenchmarkId(benchmarkId);
            item.setSortOrder(i);
            benchmarkItemMapper.insert(item);
        }

        // 3. 更新题目数和状态
        EvalRagBenchmark benchmark = getById(benchmarkId);
        if (benchmark != null) {
            benchmark.setQuestionCount(items.size());
            benchmark.setStatus("ready");
            updateById(benchmark);
        }

        log.info("[EvalBenchmark] AI 生成基准完成: knowledgeId={}, benchmarkId={}, count={}",
                knowledgeId, benchmarkId, items.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EvalRagBenchmark uploadBenchmark(Long knowledgeId, String name, String description,
                                             MultipartFile file) {
        // 1. 创建基准记录
        EvalRagBenchmark benchmark = new EvalRagBenchmark();
        benchmark.setKnowledgeId(knowledgeId);
        benchmark.setName(name);
        benchmark.setDescription(description);
        benchmark.setQuestionCount(0);
        save(benchmark);

        // 2. 解析 JSONL
        int count = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isBlank()) continue;
                try {
                    var node = objectMapper.readTree(line);
                    EvalRagBenchmarkItem item = new EvalRagBenchmarkItem();
                    item.setBenchmarkId(benchmark.getId());
                    item.setQuery(node.has("query") ? node.get("query").asText() : "");
                    item.setGoldAnswer(node.has("gold_answer") ? node.get("gold_answer").asText() : null);
                    if (node.has("gold_chunk_ids")) {
                        item.setGoldChunkIds(objectMapper.writeValueAsString(
                                objectMapper.convertValue(node.get("gold_chunk_ids"), new TypeReference<List<String>>() {})));
                    }
                    item.setSortOrder(count);
                    benchmarkItemMapper.insert(item);
                    count++;
                } catch (Exception e) {
                    log.warn("[EvalBenchmark] 跳过无效行: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "JSONL 文件解析失败: " + e.getMessage());
        }

        benchmark.setQuestionCount(count);
        updateById(benchmark);

        log.info("[EvalBenchmark] 上传基准完成: knowledgeId={}, benchmarkId={}, count={}",
                knowledgeId, benchmark.getId(), count);
        return benchmark;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBenchmark(Long knowledgeId, Long benchmarkId) {
        EvalRagBenchmark benchmark = getById(benchmarkId);
        if (benchmark == null || !benchmark.getKnowledgeId().equals(knowledgeId)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        // 删除基准和题目
        removeById(benchmarkId);
        benchmarkItemMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EvalRagBenchmarkItem>()
                        .eq(EvalRagBenchmarkItem::getBenchmarkId, benchmarkId));
    }

    @Override
    public String downloadBenchmarkAsJsonl(Long benchmarkId) {
        List<EvalRagBenchmarkItem> items = listItemsByBenchmarkId(benchmarkId);
        StringBuilder sb = new StringBuilder();
        for (EvalRagBenchmarkItem item : items) {
            try {
                var node = objectMapper.createObjectNode();
                node.put("query", item.getQuery());
                if (item.getGoldAnswer() != null) {
                    node.put("gold_answer", item.getGoldAnswer());
                }
                if (item.getGoldChunkIds() != null) {
                    node.set("gold_chunk_ids", objectMapper.readTree(item.getGoldChunkIds()));
                }
                sb.append(objectMapper.writeValueAsString(node)).append("\n");
            } catch (Exception ignored) {
            }
        }
        return sb.toString();
    }

    @Override
    public List<EvalRagBenchmarkItem> listItemsByBenchmarkId(Long benchmarkId) {
        return benchmarkItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EvalRagBenchmarkItem>()
                        .eq(EvalRagBenchmarkItem::getBenchmarkId, benchmarkId)
                        .orderByAsc(EvalRagBenchmarkItem::getSortOrder));
    }
}

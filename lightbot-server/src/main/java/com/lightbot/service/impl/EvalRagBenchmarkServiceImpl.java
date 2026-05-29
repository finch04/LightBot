package com.lightbot.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.entity.EvalRagBenchmark;
import com.lightbot.entity.EvalRagBenchmarkItem;
import com.lightbot.entity.Task;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.TaskType;
import com.lightbot.mapper.EvalRagBenchmarkItemMapper;
import com.lightbot.mapper.EvalRagBenchmarkMapper;
import com.lightbot.service.EvalRagBenchmarkService;
import com.lightbot.service.TaskService;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    private final TaskService taskService;
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
                                        Long providerId, String modelId, Integer neighborCount,
                                        java.util.function.Consumer<Integer> progressCallback) {
        // 1. AI 生成题目
        List<EvalRagBenchmarkItem> items = evaluationEngine.generateBenchmarkItems(
                knowledgeId, count, providerId, modelId, neighborCount != null ? neighborCount : 3,
                progressCallback);

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
        // 1. 预读取并校验 JSONL 内容
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isBlank()) continue;
                // 校验每行是否为合法 JSON
                try {
                    var node = objectMapper.readTree(line);
                    // 校验必需字段 query
                    if (!node.has("query") || node.get("query").asText("").isBlank()) {
                        throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                                "第 " + lineNum + " 行缺少 query 字段或为空");
                    }
                    lines.add(line);
                } catch (BizException e) {
                    throw e;
                } catch (Exception e) {
                    throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                            "第 " + lineNum + " 行 JSON 解析失败: " + e.getMessage());
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "JSONL 文件读取失败: " + e.getMessage());
        }

        if (lines.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "JSONL 文件内容为空");
        }

        // 2. 创建基准记录
        EvalRagBenchmark benchmark = new EvalRagBenchmark();
        benchmark.setKnowledgeId(knowledgeId);
        benchmark.setName(name);
        benchmark.setDescription(description);
        benchmark.setQuestionCount(0);
        save(benchmark);

        // 3. 解析并保存题目
        int count = 0;
        for (String line : lines) {
            try {
                var node = objectMapper.readTree(line);
                EvalRagBenchmarkItem item = new EvalRagBenchmarkItem();
                item.setBenchmarkId(benchmark.getId());
                item.setQuery(node.get("query").asText());
                item.setGoldAnswer(node.has("gold_answer") ? node.get("gold_answer").asText() : null);
                if (node.has("gold_chunk_ids")) {
                    item.setGoldChunkIds(objectMapper.writeValueAsString(
                            objectMapper.convertValue(node.get("gold_chunk_ids"), new TypeReference<List<String>>() {})));
                }
                item.setSortOrder(count);
                benchmarkItemMapper.insert(item);
                count++;
            } catch (Exception e) {
                log.warn("[EvalBenchmark] 保存题目失败: {}", e.getMessage());
            }
        }

        benchmark.setQuestionCount(count);
        updateById(benchmark);

        log.info("[EvalBenchmark] 上传基准完成: knowledgeId={}, benchmarkId={}, count={}",
                knowledgeId, benchmark.getId(), count);
        return benchmark;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Task uploadBenchmarkAsync(Long knowledgeId, String name, String description, MultipartFile file, Long userId) {
        // 1. 保存临时文件
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"), "lightbot", "benchmark");
        try {
            Files.createDirectories(tempDir);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "创建临时目录失败: " + e.getMessage());
        }
        Path tempFile = tempDir.resolve(UUID.randomUUID() + ".jsonl");
        try {
            file.transferTo(tempFile.toFile());
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "保存临时文件失败: " + e.getMessage());
        }

        // 2. 创建基准记录（status=generating）
        EvalRagBenchmark benchmark = new EvalRagBenchmark();
        benchmark.setKnowledgeId(knowledgeId);
        benchmark.setName(name);
        benchmark.setDescription(description);
        benchmark.setQuestionCount(0);
        benchmark.setStatus("generating");
        save(benchmark);

        // 3. 创建异步任务
        String payload = String.format("{\"benchmarkId\":%d,\"knowledgeId\":%d,\"tempFilePath\":\"%s\"}",
                benchmark.getId(), knowledgeId, tempFile.toAbsolutePath().toString().replace("\\", "\\\\"));
        Task task = taskService.createTask(TaskType.BENCHMARK_IMPORT, "基准导入 - " + name,
                userId, benchmark.getId(), payload);

        log.info("[EvalBenchmark] 异步上传任务已创建: benchmarkId={}, taskId={}", benchmark.getId(), task.getId());
        return task;
    }

    @Override
    public void importBenchmarkItems(Long benchmarkId, String tempFilePath,
                                     java.util.function.Consumer<Integer> progressCallback) {
        Path path = Path.of(tempFilePath);
        try {
            // 1. 逐行校验并解析
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8))) {
                String line;
                int lineNum = 0;
                while ((line = reader.readLine()) != null) {
                    lineNum++;
                    line = line.trim();
                    if (line.isBlank()) continue;
                    try {
                        var node = objectMapper.readTree(line);
                        if (!node.has("query") || node.get("query").asText("").isBlank()) {
                            throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                                    "第 " + lineNum + " 行缺少 query 字段或为空");
                        }
                        lines.add(line);
                    } catch (BizException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                                "第 " + lineNum + " 行 JSON 解析失败: " + e.getMessage());
                    }
                }
            }

            if (lines.isEmpty()) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "JSONL 文件内容为空");
            }

            // 2. 逐行导入，回调进度
            int total = lines.size();
            int count = 0;
            for (int i = 0; i < total; i++) {
                try {
                    var node = objectMapper.readTree(lines.get(i));
                    EvalRagBenchmarkItem item = new EvalRagBenchmarkItem();
                    item.setBenchmarkId(benchmarkId);
                    item.setQuery(node.get("query").asText());
                    item.setGoldAnswer(node.has("gold_answer") ? node.get("gold_answer").asText() : null);
                    if (node.has("gold_chunk_ids")) {
                        item.setGoldChunkIds(objectMapper.writeValueAsString(
                                objectMapper.convertValue(node.get("gold_chunk_ids"), new TypeReference<List<String>>() {})));
                    }
                    item.setSortOrder(i);
                    benchmarkItemMapper.insert(item);
                    count++;
                } catch (Exception e) {
                    log.warn("[EvalBenchmark] 保存题目失败: {}", e.getMessage());
                }
                if (progressCallback != null) {
                    progressCallback.accept((int) (((i + 1) / (double) total) * 100));
                }
            }

            // 3. 更新基准状态
            EvalRagBenchmark benchmark = getById(benchmarkId);
            if (benchmark != null) {
                benchmark.setQuestionCount(count);
                benchmark.setStatus("ready");
                updateById(benchmark);
            }

            log.info("[EvalBenchmark] 异步导入完成: benchmarkId={}, count={}", benchmarkId, count);
        } catch (BizException e) {
            // 校验失败，重置基准状态
            resetBenchmarkOnError(benchmarkId);
            throw e;
        } catch (Exception e) {
            resetBenchmarkOnError(benchmarkId);
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "导入失败: " + e.getMessage());
        } finally {
            // 清理临时文件
            try {
                Files.deleteIfExists(path);
            } catch (Exception e) {
                log.warn("[EvalBenchmark] 清理临时文件失败: {}", tempFilePath);
            }
        }
    }

    private void resetBenchmarkOnError(Long benchmarkId) {
        try {
            EvalRagBenchmark benchmark = getById(benchmarkId);
            if (benchmark != null) {
                benchmark.setStatus("ready");
                benchmark.setQuestionCount(0);
                updateById(benchmark);
            }
        } catch (Exception ignored) {
        }
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

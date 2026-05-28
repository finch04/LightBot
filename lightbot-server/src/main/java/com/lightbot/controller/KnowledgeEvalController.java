package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.Result;
import com.lightbot.dto.EvalBenchmarkGenerateRequest;
import com.lightbot.dto.EvalRunRequest;
import com.lightbot.entity.EvalRagBenchmark;
import com.lightbot.entity.EvalRagBenchmarkItem;
import com.lightbot.entity.EvalRagResult;
import com.lightbot.entity.EvalRagResultDetail;
import com.lightbot.entity.Task;
import com.lightbot.enums.TaskType;
import com.lightbot.service.EvalRagBenchmarkService;
import com.lightbot.service.EvalRagResultService;
import com.lightbot.service.TaskService;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库 RAG 评估接口
 *
 * @author finch
 * @since 2026-05-28
 */
@Tag(name = "RAG 评估", description = "知识库 RAG 评估基准与评估结果管理")
@RestController
@RequestMapping("/api/knowledge/{knowledgeId}/eval")
@RequiredArgsConstructor
public class KnowledgeEvalController {

    private final EvalRagBenchmarkService benchmarkService;
    private final EvalRagResultService resultService;
    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    // ==================== 评估基准 ====================

    @GetMapping("/benchmarks")
    @Operation(summary = "获取评估基准列表")
    public Result<List<EvalRagBenchmark>> listBenchmarks(@PathVariable Long knowledgeId) {
        return Result.ok(benchmarkService.listByKnowledgeId(knowledgeId));
    }

    @GetMapping("/benchmarks/{benchmarkId}")
    @Operation(summary = "获取评估基准详情（含分页题目）")
    public Result<Page<EvalRagBenchmarkItem>> getBenchmarkDetail(
            @PathVariable Long knowledgeId,
            @PathVariable Long benchmarkId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(benchmarkService.getBenchmarkDetail(benchmarkId, pageNum, pageSize));
    }

    @PostMapping("/benchmarks/generate")
    @Operation(summary = "AI 自动生成评估基准（异步任务）")
    public Result<Task> generateBenchmark(
            @PathVariable Long knowledgeId,
            @RequestBody @Valid EvalBenchmarkGenerateRequest request) throws Exception {
        // 1. 创建空基准记录
        EvalRagBenchmark benchmark = benchmarkService.createEmptyBenchmark(
                knowledgeId, request.getName(), request.getDescription());

        // 2. 构造任务参数
        long userId = StpUtil.getLoginIdAsLong();
        var payloadNode = objectMapper.createObjectNode();
        payloadNode.put("benchmarkId", benchmark.getId());
        payloadNode.put("knowledgeId", knowledgeId);
        payloadNode.put("count", request.getCount());
        if (request.getProviderId() != null) {
            payloadNode.put("providerId", request.getProviderId());
        }
        if (request.getModelId() != null && !request.getModelId().isBlank()) {
            payloadNode.put("modelId", request.getModelId());
        }
        if (request.getNeighborCount() != null) {
            payloadNode.put("neighborCount", request.getNeighborCount());
        }

        // 3. 创建异步任务
        Task task = taskService.createTask(
                TaskType.BENCHMARK_GENERATE,
                "AI 生成基准 - " + request.getName(),
                userId,
                benchmark.getId(),
                objectMapper.writeValueAsString(payloadNode));

        return Result.ok(task);
    }

    @PostMapping("/benchmarks/upload")
    @Operation(summary = "上传 JSONL 评估基准")
    public Result<EvalRagBenchmark> uploadBenchmark(
            @PathVariable Long knowledgeId,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("file") MultipartFile file) {
        return Result.ok(benchmarkService.uploadBenchmark(knowledgeId, name, description, file));
    }

    @DeleteMapping("/benchmarks/{benchmarkId}")
    @Operation(summary = "删除评估基准")
    public Result<Void> deleteBenchmark(
            @PathVariable Long knowledgeId,
            @PathVariable Long benchmarkId) {
        benchmarkService.deleteBenchmark(knowledgeId, benchmarkId);
        return Result.ok();
    }

    @GetMapping("/benchmarks/{benchmarkId}/download")
    @Operation(summary = "下载评估基准为 JSONL")
    public ResponseEntity<byte[]> downloadBenchmark(
            @PathVariable Long knowledgeId,
            @PathVariable Long benchmarkId) {
        String jsonl = benchmarkService.downloadBenchmarkAsJsonl(benchmarkId);
        byte[] bytes = jsonl.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=benchmark.jsonl")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(bytes.length)
                .body(bytes);
    }

    // ==================== 评估结果 ====================

    @PostMapping("/run")
    @Operation(summary = "运行 RAG 评估（异步任务）")
    public Result<Task> runEvaluation(
            @PathVariable Long knowledgeId,
            @RequestBody @Valid EvalRunRequest request) throws Exception {
        // 1. 创建评估结果记录
        EvalRagResult result = resultService.createEvalResult(knowledgeId, request.getBenchmarkId());

        // 2. 构造任务参数
        var payloadNode = objectMapper.createObjectNode();
        payloadNode.put("resultId", result.getId());
        payloadNode.put("benchmarkId", request.getBenchmarkId());
        payloadNode.put("knowledgeId", knowledgeId);
        if (request.getAnswerProviderId() != null) {
            payloadNode.put("answerProviderId", request.getAnswerProviderId());
        }
        if (request.getAnswerModelId() != null) {
            payloadNode.put("answerModelId", request.getAnswerModelId());
        }
        if (request.getJudgeProviderId() != null) {
            payloadNode.put("judgeProviderId", request.getJudgeProviderId());
        }
        if (request.getJudgeModelId() != null) {
            payloadNode.put("judgeModelId", request.getJudgeModelId());
        }

        // 3. 创建异步任务
        Long userId = StpUtil.getLoginIdAsLong();
        Task task = taskService.createTask(
                TaskType.RAG_EVALUATION,
                "RAG评估(" + result.getBenchmarkName() + ")",
                userId,
                result.getId(),
                objectMapper.writeValueAsString(payloadNode));
        return Result.ok(task);
    }

    @GetMapping("/results")
    @Operation(summary = "获取评估历史")
    public Result<Page<EvalRagResult>> listResults(
            @PathVariable Long knowledgeId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(resultService.listByKnowledgeId(knowledgeId, pageNum, pageSize));
    }

    @GetMapping("/results/{resultId}")
    @Operation(summary = "获取评估结果详情（含分页明细）")
    public Result<Page<EvalRagResultDetail>> getResultDetail(
            @PathVariable Long knowledgeId,
            @PathVariable Long resultId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "false") boolean errorOnly) {
        return Result.ok(resultService.getResultDetail(resultId, pageNum, pageSize, errorOnly));
    }

    @DeleteMapping("/results/{resultId}")
    @Operation(summary = "删除评估结果")
    public Result<Void> deleteResult(
            @PathVariable Long knowledgeId,
            @PathVariable Long resultId) {
        resultService.deleteResult(knowledgeId, resultId);
        return Result.ok();
    }
}

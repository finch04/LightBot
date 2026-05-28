package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.common.Result;
import com.lightbot.dto.EvalExperimentCreateRequest;
import com.lightbot.dto.EvalExperimentOverviewVO;
import com.lightbot.entity.EvalExperiment;
import com.lightbot.entity.EvalExperimentResult;
import com.lightbot.service.EvalExperimentResultService;
import com.lightbot.service.EvalExperimentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 评测实验管理接口
 *
 * @author finch
 * @since 2026-05-27
 */
@Tag(name = "评测实验管理", description = "评测实验的增删改查")
@RestController
@RequestMapping("/api/eval/experiments")
@RequiredArgsConstructor
public class EvalExperimentController {

    private final EvalExperimentService experimentService;
    private final EvalExperimentResultService experimentResultService;

    @Operation(summary = "创建实验")
    @PostMapping
    public Result<EvalExperiment> create(@RequestBody EvalExperimentCreateRequest request) {
        return Result.ok(experimentService.create(
                request.getName(), request.getDescription(),
                request.getDatasetId(), request.getDatasetVersionId(), request.getDatasetVersion(),
                request.getEvaluationObjectConfig(), request.getEvaluatorConfig(), null));
    }

    @Operation(summary = "获取实验详情")
    @GetMapping("/{id}")
    public Result<EvalExperiment> getById(@PathVariable Long id) {
        return Result.ok(experimentService.getDetail(id));
    }

    @Operation(summary = "获取实验列表")
    @GetMapping
    public Result<Page<EvalExperiment>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.ok(experimentService.list(pageNum, pageSize, keyword, status, null));
    }

    @Operation(summary = "停止实验")
    @PutMapping("/{id}/stop")
    public Result<Void> stop(@PathVariable Long id) {
        experimentService.stop(id, null);
        return Result.ok();
    }

    @Operation(summary = "删除实验")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        experimentService.deleteById(id, null);
        return Result.ok();
    }

    @Operation(summary = "重启实验")
    @PutMapping("/{id}/restart")
    public Result<EvalExperiment> restart(@PathVariable Long id) {
        return Result.ok(experimentService.restart(id, null));
    }

    @Operation(summary = "获取实验结果概览")
    @GetMapping("/{id}/results")
    public Result<List<EvalExperimentOverviewVO>> getResults(@PathVariable Long id) {
        return Result.ok(experimentResultService.getOverview(id));
    }

    @Operation(summary = "获取实验详细结果")
    @GetMapping("/{id}/detail")
    public Result<Page<EvalExperimentResult>> getDetailResults(
            @PathVariable Long id,
            @RequestParam(required = false) Long evaluatorVersionId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(experimentResultService.listByExperiment(id, evaluatorVersionId, pageNum, pageSize));
    }
}

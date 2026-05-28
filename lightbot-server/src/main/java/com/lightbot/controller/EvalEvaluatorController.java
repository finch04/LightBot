package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.common.Result;
import com.lightbot.dto.EvalEvaluatorCreateRequest;
import com.lightbot.dto.EvalEvaluatorTestRequest;
import com.lightbot.dto.EvalEvaluatorVersionCreateRequest;
import com.lightbot.dto.EvalScoreResult;
import com.lightbot.entity.EvalEvaluator;
import com.lightbot.entity.EvalEvaluatorTemplate;
import com.lightbot.entity.EvalEvaluatorVersion;
import com.lightbot.service.EvalChatService;
import com.lightbot.service.EvalEvaluatorService;
import com.lightbot.service.EvalEvaluatorTemplateService;
import com.lightbot.service.EvalEvaluatorVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 评估器管理接口
 *
 * @author finch
 * @since 2026-05-27
 */
@Tag(name = "评估器管理", description = "评估器的增删改查及版本管理")
@RestController
@RequestMapping("/api/eval/evaluators")
@RequiredArgsConstructor
public class EvalEvaluatorController {

    private final EvalEvaluatorService evaluatorService;
    private final EvalEvaluatorVersionService evaluatorVersionService;
    private final EvalEvaluatorTemplateService evaluatorTemplateService;
    private final EvalChatService evalChatService;

    @Operation(summary = "创建评估器")
    @PostMapping
    public Result<EvalEvaluator> create(@RequestBody EvalEvaluatorCreateRequest request) {
        return Result.ok(evaluatorService.create(request.getName(), request.getDescription(), null));
    }

    @Operation(summary = "获取评估器详情")
    @GetMapping("/{id}")
    public Result<EvalEvaluator> getById(@PathVariable Long id) {
        return Result.ok(evaluatorService.getById(id));
    }

    @Operation(summary = "获取评估器列表")
    @GetMapping
    public Result<Page<EvalEvaluator>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.ok(evaluatorService.list(pageNum, pageSize, keyword, null));
    }

    @Operation(summary = "更新评估器")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody EvalEvaluatorCreateRequest request) {
        evaluatorService.update(id, request.getName(), request.getDescription());
        return Result.ok();
    }

    @Operation(summary = "删除评估器")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        evaluatorService.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "创建评估器版本")
    @PostMapping("/versions")
    public Result<EvalEvaluatorVersion> createVersion(@RequestBody EvalEvaluatorVersionCreateRequest request) {
        return Result.ok(evaluatorVersionService.create(
                request.getEvaluatorId(), request.getVersion(),
                request.getPrompt(), request.getVariables(), request.getModelConfig()));
    }

    @Operation(summary = "获取评估器版本列表")
    @GetMapping("/{evaluatorId}/versions")
    public Result<List<EvalEvaluatorVersion>> listVersions(@PathVariable Long evaluatorId) {
        return Result.ok(evaluatorVersionService.listByEvaluatorId(evaluatorId));
    }

    @Operation(summary = "获取评估器模板列表")
    @GetMapping("/templates")
    public Result<List<EvalEvaluatorTemplate>> listTemplates() {
        return Result.ok(evaluatorTemplateService.listAll());
    }

    @Operation(summary = "获取评估器模板详情")
    @GetMapping("/templates/{key}")
    public Result<EvalEvaluatorTemplate> getTemplate(@PathVariable String key) {
        return Result.ok(evaluatorTemplateService.getByKey(key));
    }

    @Operation(summary = "测试评估器")
    @PostMapping("/test")
    public Result<EvalScoreResult> test(@RequestBody EvalEvaluatorTestRequest request) {
        EvalEvaluatorVersion version = evaluatorVersionService.getById(request.getEvaluatorVersionId());
        if (version == null) {
            return Result.ok(null);
        }
        EvalScoreResult result = evalChatService.callEvaluator(
                version.getModelConfig(), version.getPrompt(), request.getVariables());
        return Result.ok(result);
    }
}

package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.common.Result;
import com.lightbot.dto.EvalDatasetCreateRequest;
import com.lightbot.dto.EvalDatasetExampleVO;
import com.lightbot.dto.EvalDatasetItemCreateRequest;
import com.lightbot.dto.EvalDatasetVersionCreateRequest;
import com.lightbot.entity.EvalDataset;
import com.lightbot.entity.EvalDatasetItem;
import com.lightbot.entity.EvalDatasetVersion;
import com.lightbot.service.EvalDatasetItemService;
import com.lightbot.service.EvalDatasetService;
import com.lightbot.service.EvalDatasetVersionService;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 评测集管理接口
 *
 * @author finch
 * @since 2026-05-27
 */
@Tag(name = "评测集管理", description = "评测集的增删改查及版本管理")
@RestController
@RequestMapping("/api/eval/datasets")
@RequiredArgsConstructor
public class EvalDatasetController {

    private final EvalDatasetService datasetService;
    private final EvalDatasetVersionService datasetVersionService;
    private final EvalDatasetItemService datasetItemService;

    @Operation(summary = "创建评测集")
    @PostMapping
    public Result<EvalDataset> create(@Valid @RequestBody EvalDatasetCreateRequest request) {
        return Result.ok(datasetService.create(request.getName(), request.getDescription(), request.getColumnsConfig(), StpUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "获取评测集详情")
    @GetMapping("/{id}")
    public Result<EvalDataset> getById(@PathVariable Long id) {
        return Result.ok(datasetService.getById(id));
    }

    @Operation(summary = "获取评测集列表")
    @GetMapping
    public Result<Page<EvalDataset>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.ok(datasetService.list(pageNum, pageSize, keyword, StpUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "更新评测集")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody EvalDatasetCreateRequest request) {
        datasetService.update(id, request.getName(), request.getDescription(), request.getColumnsConfig());
        return Result.ok();
    }

    @Operation(summary = "删除评测集")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        datasetService.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "创建评测集版本")
    @PostMapping("/versions")
    public Result<EvalDatasetVersion> createVersion(@Valid @RequestBody EvalDatasetVersionCreateRequest request) {
        return Result.ok(datasetVersionService.create(request.getDatasetId(), request.getVersion()));
    }

    @Operation(summary = "获取评测集版本列表")
    @GetMapping("/{datasetId}/versions")
    public Result<List<EvalDatasetVersion>> listVersions(@PathVariable Long datasetId) {
        return Result.ok(datasetVersionService.listByDatasetId(datasetId));
    }

    @Operation(summary = "获取版本的数据项快照")
    @GetMapping("/versions/{versionId}/items")
    public Result<List<EvalDatasetItem>> listVersionItems(@PathVariable Long versionId) {
        return Result.ok(datasetVersionService.getItemsByVersionId(versionId));
    }

    @Operation(summary = "添加评测数据项")
    @PostMapping("/items")
    public Result<EvalDatasetItem> addItem(@Valid @RequestBody EvalDatasetItemCreateRequest request) {
        return Result.ok(datasetItemService.create(request.getDatasetId(), request.getDataContent()));
    }

    @Operation(summary = "批量添加评测数据项")
    @PostMapping("/items/batch")
    public Result<Integer> batchAddItems(@Valid @RequestBody EvalDatasetItemCreateRequest request) {
        return Result.ok(datasetItemService.batchCreate(request.getDatasetId(), request.getDataContents()));
    }

    @Operation(summary = "获取评测数据项列表")
    @GetMapping("/items")
    public Result<Page<EvalDatasetItem>> listItems(
            @RequestParam Long datasetId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(datasetItemService.listByDatasetId(datasetId, pageNum, pageSize));
    }

    @Operation(summary = "删除评测数据项")
    @DeleteMapping("/items/{id}")
    public Result<Void> deleteItem(@PathVariable Long id) {
        datasetItemService.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "获取示例评测集列表")
    @GetMapping("/examples")
    public Result<List<EvalDatasetExampleVO>> listExamples() {
        return Result.ok(datasetService.listExamples());
    }

    @Operation(summary = "从示例模板创建评测集")
    @PostMapping("/examples/{key}")
    public Result<EvalDataset> createFromExample(@PathVariable String key) {
        return Result.ok(datasetService.createFromExample(key, StpUtil.getLoginIdAsLong()));
    }
}

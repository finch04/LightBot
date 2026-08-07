package com.lightbot.controller;

import com.lightbot.common.Result;
import com.lightbot.dto.askdata.AskDataIntentIR;
import com.lightbot.dto.askdata.AskDatasetEnhanceDTO;
import com.lightbot.dto.askdata.AskDatasetPreviewDTO;
import com.lightbot.dto.askdata.AskDatasetSaveDTO;
import com.lightbot.dto.askdata.AskRelationSaveDTO;
import com.lightbot.entity.AskRelation;
import com.lightbot.service.AskDataQueryService;
import com.lightbot.service.AskDatasetService;
import com.lightbot.vo.AskDataResultVO;
import com.lightbot.vo.AskDatasetVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 智能问数：语义配置与轻量增强 API
 *
 * @author finch
 * @since 2026-07-30
 */
@Tag(name = "智能问数")
@RestController
@RequestMapping("/api/ask-data")
@RequiredArgsConstructor
public class AskDatasetController {

    private final AskDatasetService askDatasetService;
    private final AskDataQueryService askDataQueryService;

    @GetMapping("/datasets")
    @Operation(summary = "问数数据集列表")
    public Result<List<AskDatasetVO>> listDatasets(@RequestParam(required = false) String keyword) {
        return Result.ok(askDatasetService.listAll(keyword));
    }

    @GetMapping("/datasets/{id}")
    @Operation(summary = "问数数据集详情")
    public Result<AskDatasetVO> getDataset(@PathVariable Long id) {
        return Result.ok(askDatasetService.getDetail(id));
    }

    @PostMapping("/datasets")
    @Operation(summary = "创建问数数据集")
    public Result<AskDatasetVO> createDataset(@Valid @RequestBody AskDatasetSaveDTO dto) {
        return Result.ok(askDatasetService.create(dto));
    }

    @PutMapping("/datasets/{id}")
    @Operation(summary = "更新问数数据集")
    public Result<AskDatasetVO> updateDataset(@PathVariable Long id,
                                              @Valid @RequestBody AskDatasetSaveDTO dto) {
        return Result.ok(askDatasetService.update(id, dto));
    }

    @PutMapping("/datasets/{id}/enhancement")
    @Operation(summary = "轻量问数增强（说明/时间/敏感字段/默认过滤/业务指标）")
    public Result<AskDatasetVO> updateEnhancement(@PathVariable Long id,
                                                  @Valid @RequestBody AskDatasetEnhanceDTO dto) {
        return Result.ok(askDatasetService.updateEnhancement(id, dto));
    }

    @DeleteMapping("/datasets/{id}")
    @Operation(summary = "删除问数数据集")
    public Result<Void> deleteDataset(@PathVariable Long id) {
        askDatasetService.delete(id);
        return Result.ok();
    }

    @PostMapping("/datasets/{id}/refresh-profile")
    @Operation(summary = "刷新字段画像")
    public Result<AskDatasetVO> refreshProfile(@PathVariable Long id) {
        return Result.ok(askDatasetService.refreshProfile(id));
    }

    @PostMapping("/datasets/ensure-from-model/{dataModelId}")
    @Operation(summary = "模型即可问：确保数据模型已有问数配置（自动同步字段语义）")
    public Result<AskDatasetVO> ensureFromModel(@PathVariable Long dataModelId) {
        return Result.ok(askDatasetService.ensureFromModel(dataModelId));
    }

    @PostMapping("/datasets/{id}/sync-from-model")
    @Operation(summary = "从数据模型同步维度与默认指标（保留自定义指标与同义词）")
    public Result<AskDatasetVO> syncFromModel(@PathVariable Long id) {
        return Result.ok(askDatasetService.syncFromModel(id));
    }

    @GetMapping("/datasets/by-model/{dataModelId}")
    @Operation(summary = "按数据模型查询问数配置")
    public Result<AskDatasetVO> findByModel(@PathVariable Long dataModelId) {
        return Result.ok(askDatasetService.findByDataModelId(dataModelId));
    }

    @GetMapping("/relations")
    @Operation(summary = "问数关联列表")
    public Result<List<AskRelation>> listRelations() {
        return Result.ok(askDatasetService.listRelations());
    }

    @PostMapping("/relations")
    @Operation(summary = "创建问数关联")
    public Result<AskRelation> createRelation(@Valid @RequestBody AskRelationSaveDTO dto) {
        return Result.ok(askDatasetService.createRelation(dto));
    }

    @DeleteMapping("/relations/{id}")
    @Operation(summary = "删除问数关联")
    public Result<Void> deleteRelation(@PathVariable Long id) {
        askDatasetService.deleteRelation(id);
        return Result.ok();
    }

    @PostMapping("/query")
    @Operation(summary = "调试执行 Intent IR（控制台，不限 Agent 白名单）")
    public Result<AskDataResultVO> query(@RequestBody AskDataIntentIR ir) {
        return Result.ok(askDataQueryService.execute(ir, null));
    }

    @PostMapping("/datasets/{id}/preview")
    @Operation(summary = "问数增强预览（默认过滤 / 业务指标试跑，不落库）")
    public Result<AskDataResultVO> previewEnhancement(@PathVariable Long id,
                                                      @Valid @RequestBody AskDatasetPreviewDTO dto) {
        return Result.ok(askDataQueryService.previewEnhancement(id, dto));
    }
}

package com.lightbot.controller;

import com.lightbot.common.Result;
import com.lightbot.dto.datacenter.DataModelCreateDTO;
import com.lightbot.dto.datacenter.DataModelFieldKeySuggestDTO;
import com.lightbot.dto.datacenter.DataModelSchemaUpdateDTO;
import com.lightbot.dto.datacenter.DataModelUpdateDTO;
import com.lightbot.service.DataModelService;
import com.lightbot.vo.DataModelFieldKeySuggestVO;
import com.lightbot.vo.DataModelVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据模型接口
 *
 * @author finch
 * @since 2026-07-26
 */
@Tag(name = "数据模型")
@RestController
@RequestMapping("/api/data-models")
@RequiredArgsConstructor
public class DataModelController {

    private final DataModelService dataModelService;

    @GetMapping
    @Operation(summary = "数据模型列表")
    public Result<List<DataModelVO>> list(@RequestParam(required = false) Long categoryId,
                                          @RequestParam(required = false) String keyword) {
        return Result.ok(dataModelService.listMine(categoryId, keyword));
    }

    @GetMapping("/{id}")
    @Operation(summary = "数据模型详情")
    public Result<DataModelVO> detail(@PathVariable Long id) {
        return Result.ok(dataModelService.getMine(id));
    }

    @PostMapping
    @Operation(summary = "新建数据模型")
    public Result<DataModelVO> create(@Valid @RequestBody DataModelCreateDTO dto) {
        return Result.ok(dataModelService.create(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新数据模型基础信息")
    public Result<DataModelVO> update(@PathVariable Long id,
                                      @Valid @RequestBody DataModelUpdateDTO dto) {
        return Result.ok(dataModelService.updateInfo(id, dto));
    }

    @PutMapping("/{id}/schema")
    @Operation(summary = "保存表单结构与索引配置（并同步物理表）")
    public Result<DataModelVO> updateSchema(@PathVariable Long id,
                                            @Valid @RequestBody DataModelSchemaUpdateDTO dto) {
        return Result.ok(dataModelService.updateSchema(id, dto.getSchema()));
    }

    @PostMapping("/suggest-field-keys")
    @Operation(summary = "AI 补全字段英文名（仅针对请求中的空英文名字段）")
    public Result<DataModelFieldKeySuggestVO> suggestFieldKeys(
            @Valid @RequestBody DataModelFieldKeySuggestDTO dto) {
        return Result.ok(dataModelService.suggestFieldKeys(dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除数据模型")
    public Result<Void> delete(@PathVariable Long id) {
        dataModelService.delete(id);
        return Result.ok();
    }
}

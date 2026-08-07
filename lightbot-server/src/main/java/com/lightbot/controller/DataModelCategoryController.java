package com.lightbot.controller;

import com.lightbot.common.Result;
import com.lightbot.dto.datacenter.DataModelCategorySaveDTO;
import com.lightbot.entity.DataModelCategory;
import com.lightbot.service.DataModelCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据模型分类接口
 *
 * @author finch
 * @since 2026-07-26
 */
@Tag(name = "数据模型分类")
@RestController
@RequestMapping("/api/data-model-categories")
@RequiredArgsConstructor
public class DataModelCategoryController {

    private final DataModelCategoryService categoryService;

    @GetMapping
    @Operation(summary = "分类列表")
    public Result<List<DataModelCategory>> list() {
        return Result.ok(categoryService.listMine());
    }

    @PostMapping
    @Operation(summary = "新建分类")
    public Result<DataModelCategory> create(@Valid @RequestBody DataModelCategorySaveDTO dto) {
        return Result.ok(categoryService.create(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "重命名分类")
    public Result<DataModelCategory> update(@PathVariable Long id,
                                            @Valid @RequestBody DataModelCategorySaveDTO dto) {
        return Result.ok(categoryService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除分类")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.ok();
    }
}

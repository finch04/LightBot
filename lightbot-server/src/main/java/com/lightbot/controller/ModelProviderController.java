package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.common.Result;
import com.lightbot.dto.ModelProviderRequest;
import com.lightbot.entity.ModelProvider;
import com.lightbot.service.ModelProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "模型提供商管理", description = "模型提供商的增删改查")
@RestController
@RequestMapping("/api/model-providers")
@RequiredArgsConstructor
public class ModelProviderController {

    private final ModelProviderService modelProviderService;

    @Operation(summary = "新增模型提供商")
    @PostMapping
    public Result<ModelProvider> create(@Valid @RequestBody ModelProviderRequest request) {
        return Result.ok(modelProviderService.create(request));
    }

    @Operation(summary = "更新模型提供商")
    @PutMapping
    public Result<ModelProvider> update(@Valid @RequestBody ModelProviderRequest request) {
        return Result.ok(modelProviderService.update(request));
    }

    @Operation(summary = "删除模型提供商")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        modelProviderService.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "分页查询模型提供商")
    @GetMapping
    public Result<Page<ModelProvider>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(modelProviderService.listPage(pageNum, pageSize));
    }

    @Operation(summary = "获取单个模型提供商")
    @GetMapping("/{id}")
    public Result<ModelProvider> getById(@PathVariable Long id) {
        return Result.ok(modelProviderService.getById(id));
    }
}

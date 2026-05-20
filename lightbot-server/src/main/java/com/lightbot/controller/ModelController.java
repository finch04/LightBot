package com.lightbot.controller;

import com.lightbot.common.Result;
import com.lightbot.dto.ModelRequest;
import com.lightbot.entity.Model;
import com.lightbot.enums.ModelType;
import com.lightbot.service.ModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "模型管理", description = "模型的增删改查")
@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class ModelController {

    private final ModelService modelService;

    @Operation(summary = "新增模型")
    @PostMapping
    public Result<Model> create(@Valid @RequestBody ModelRequest request) {
        return Result.ok(modelService.create(request));
    }

    @Operation(summary = "删除模型")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        modelService.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "获取指定提供商下的模型列表")
    @GetMapping("/by-provider/{providerId}")
    public Result<List<Model>> listByProvider(@PathVariable Long providerId) {
        return Result.ok(modelService.listByProviderId(providerId));
    }

    @Operation(summary = "按类型获取所有可用模型")
    @GetMapping("/by-type/{type}")
    public Result<List<Model>> listByType(@PathVariable String type) {
        return Result.ok(modelService.listByType(ModelType.fromValue(type)));
    }
}

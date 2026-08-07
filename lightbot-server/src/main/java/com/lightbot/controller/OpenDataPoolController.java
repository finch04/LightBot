package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.Result;
import com.lightbot.dto.datacenter.DataPoolBatchCreateDTO;
import com.lightbot.dto.datacenter.DataPoolBatchDeleteDTO;
import com.lightbot.dto.datacenter.DataPoolRecordDTO;
import com.lightbot.service.DataModelService;
import com.lightbot.service.DataPoolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 数据池开放 API（供外部系统通过登录会话或 API Key 调用）。
 * <p>
 * 路径不含动态参数；模型以物理表名 {@code tableName}（如 sjc_data_customer）标识，
 * 记录以 Query {@code recordId} 标识。
 *
 * @author finch
 * @since 2026-07-26
 */
@Tag(name = "开放API-数据池")
@RestController
@RequestMapping("/api/open/v1/data-pools/records")
@RequiredArgsConstructor
@Validated
public class OpenDataPoolController {

    private final DataPoolService dataPoolService;
    private final DataModelService dataModelService;
    private final ObjectMapper objectMapper;

    @GetMapping
    @Operation(summary = "查询列表（分页）")
    public Result<Page<Map<String, Object>>> page(
            @RequestParam @NotBlank String tableName,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String filters) {
        Long modelId = resolveModelId(tableName);
        return Result.ok(dataPoolService.page(modelId, pageNum, pageSize, keyword, parseFilters(filters)));
    }

    @PostMapping
    @Operation(summary = "新增")
    public Result<Map<String, Object>> create(@RequestParam @NotBlank String tableName,
                                              @Valid @RequestBody DataPoolRecordDTO dto) {
        return Result.ok(dataPoolService.create(resolveModelId(tableName), dto.getData()));
    }

    @PostMapping("/batch")
    @Operation(summary = "批量新增")
    public Result<List<Map<String, Object>>> batchCreate(@RequestParam @NotBlank String tableName,
                                                         @Valid @RequestBody DataPoolBatchCreateDTO dto) {
        return Result.ok(dataPoolService.batchCreate(resolveModelId(tableName), dto.getRecords()));
    }

    @PutMapping
    @Operation(summary = "修改")
    public Result<Map<String, Object>> update(@RequestParam @NotBlank String tableName,
                                              @RequestParam @NotNull Long recordId,
                                              @Valid @RequestBody DataPoolRecordDTO dto) {
        return Result.ok(dataPoolService.update(resolveModelId(tableName), recordId, dto.getData()));
    }

    @DeleteMapping
    @Operation(summary = "删除")
    public Result<Void> delete(@RequestParam @NotBlank String tableName,
                               @RequestParam @NotNull Long recordId) {
        dataPoolService.delete(resolveModelId(tableName), recordId);
        return Result.ok();
    }

    @PostMapping("/batch-delete")
    @Operation(summary = "批量删除")
    public Result<Integer> batchDelete(@RequestParam @NotBlank String tableName,
                                       @Valid @RequestBody DataPoolBatchDeleteDTO dto) {
        return Result.ok(dataPoolService.batchDelete(resolveModelId(tableName), dto.getIds()));
    }

    /**
     * 将物理表名解析为当前用户拥有的数据模型 ID。
     *
     * @param tableName 完整表名，如 sjc_data_customer
     * @return 模型 ID
     */
    private Long resolveModelId(String tableName) {
        return dataModelService.requireOwnedByTableName(tableName).getId();
    }

    private Map<String, Object> parseFilters(String filters) {
        if (!StringUtils.hasText(filters)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(filters, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}

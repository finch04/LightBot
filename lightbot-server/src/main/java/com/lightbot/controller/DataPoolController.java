package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.Result;
import com.lightbot.dto.datacenter.DataPoolBatchCreateDTO;
import com.lightbot.dto.datacenter.DataPoolBatchDeleteDTO;
import com.lightbot.dto.datacenter.DataPoolRecordDTO;
import com.lightbot.service.DataPoolService;
import com.lightbot.vo.DataPoolImportResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 数据池 UI 接口（导入导出 + CRUD）
 *
 * @author finch
 * @since 2026-07-26
 */
@Tag(name = "数据池")
@RestController
@RequestMapping("/api/data-pools/{modelId}")
@RequiredArgsConstructor
public class DataPoolController {

    private final DataPoolService dataPoolService;
    private final ObjectMapper objectMapper;

    @GetMapping("/records")
    @Operation(summary = "分页查询数据池记录")
    public Result<Page<Map<String, Object>>> page(
            @PathVariable Long modelId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String filters) {
        return Result.ok(dataPoolService.page(modelId, pageNum, pageSize, keyword, parseFilters(filters)));
    }

    @GetMapping("/records/{recordId}")
    @Operation(summary = "记录详情")
    public Result<Map<String, Object>> detail(@PathVariable Long modelId, @PathVariable Long recordId) {
        return Result.ok(dataPoolService.get(modelId, recordId));
    }

    @PostMapping("/records")
    @Operation(summary = "新增记录")
    public Result<Map<String, Object>> create(@PathVariable Long modelId,
                                              @Valid @RequestBody DataPoolRecordDTO dto) {
        return Result.ok(dataPoolService.create(modelId, dto.getData()));
    }

    @PostMapping("/records/batch")
    @Operation(summary = "批量新增")
    public Result<List<Map<String, Object>>> batchCreate(@PathVariable Long modelId,
                                                         @Valid @RequestBody DataPoolBatchCreateDTO dto) {
        return Result.ok(dataPoolService.batchCreate(modelId, dto.getRecords()));
    }

    @PutMapping("/records/{recordId}")
    @Operation(summary = "修改记录")
    public Result<Map<String, Object>> update(@PathVariable Long modelId,
                                              @PathVariable Long recordId,
                                              @Valid @RequestBody DataPoolRecordDTO dto) {
        return Result.ok(dataPoolService.update(modelId, recordId, dto.getData()));
    }

    @DeleteMapping("/records/{recordId}")
    @Operation(summary = "删除记录")
    public Result<Void> delete(@PathVariable Long modelId, @PathVariable Long recordId) {
        dataPoolService.delete(modelId, recordId);
        return Result.ok();
    }

    @PostMapping("/records/batch-delete")
    @Operation(summary = "批量删除")
    public Result<Integer> batchDelete(@PathVariable Long modelId,
                                       @Valid @RequestBody DataPoolBatchDeleteDTO dto) {
        return Result.ok(dataPoolService.batchDelete(modelId, dto.getIds()));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "同步导入 CSV/JSON，返回统计与结果 Excel（Base64）")
    public Result<DataPoolImportResultVO> importData(@PathVariable Long modelId,
                                                     @RequestPart("file") MultipartFile file,
                                                     @RequestParam(defaultValue = "append") String mode) {
        return Result.ok(dataPoolService.importData(modelId, file, mode));
    }

    @GetMapping("/export")
    @Operation(summary = "导出数据（format=json|csv）")
    public ResponseEntity<byte[]> export(@PathVariable Long modelId,
                                         @RequestParam(defaultValue = "json") String format,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String filters) {
        Map<String, Object> filterMap = parseFilters(filters);
        boolean csv = "csv".equalsIgnoreCase(format);
        byte[] body = csv
                ? dataPoolService.exportCsv(modelId, keyword, filterMap)
                : dataPoolService.exportJson(modelId, keyword, filterMap);
        String filename = "data-pool-" + modelId + (csv ? ".csv" : ".json");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(csv
                        ? new MediaType("text", "csv", StandardCharsets.UTF_8)
                        : MediaType.APPLICATION_JSON)
                .body(body);
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

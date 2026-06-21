package com.lightbot.controller;

import com.lightbot.common.Result;
import com.lightbot.dto.QaPairCreateDTO;
import com.lightbot.dto.QaPairUpdateDTO;
import com.lightbot.dto.QaPairVO;
import com.lightbot.entity.Task;
import com.lightbot.service.QaPairService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库问答对管理接口
 *
 * @author finch
 * @since 2026-06-21
 */
@Tag(name = "知识库问答对管理", description = "问答对CRUD、批量导入、AI生成、向量化")
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeQAPairController {

    private final QaPairService qaPairService;

    @Operation(summary = "创建问答对（需要DEVELOPER及以上权限）")
    @PostMapping("/{id}/qa-pairs")
    public Result<QaPairVO> createQaPair(@PathVariable Long id,
                                          @Valid @RequestBody QaPairCreateDTO dto) {
        return Result.ok(qaPairService.create(id, dto));
    }

    @Operation(summary = "更新问答对（需要DEVELOPER及以上权限）")
    @PutMapping("/qa-pairs/{qaPairId}")
    public Result<QaPairVO> updateQaPair(@PathVariable Long qaPairId,
                                          @Valid @RequestBody QaPairUpdateDTO dto) {
        dto.setId(qaPairId);
        return Result.ok(qaPairService.update(dto));
    }

    @Operation(summary = "分页查询问答对列表（需要成员权限）")
    @GetMapping("/{id}/qa-pairs")
    public Result<?> listQaPairs(@PathVariable Long id,
                                  @RequestParam(defaultValue = "1") int pageNum,
                                  @RequestParam(defaultValue = "20") int pageSize,
                                  @RequestParam(required = false) String keyword) {
        return Result.ok(qaPairService.listByKnowledgeId(id, pageNum, pageSize, keyword));
    }

    @Operation(summary = "删除问答对（需要DEVELOPER及以上权限）")
    @DeleteMapping("/qa-pairs/{qaPairId}")
    public Result<Void> deleteQaPair(@PathVariable Long qaPairId) {
        qaPairService.deleteById(qaPairId);
        return Result.ok();
    }

    @Operation(summary = "批量导入问答对（需要DEVELOPER及以上权限）")
    @PostMapping("/{id}/qa-pairs/batch-import")
    public Result<Integer> batchImportQaPairs(@PathVariable Long id,
                                               @Valid @RequestBody List<QaPairCreateDTO> items) {
        return Result.ok(qaPairService.batchImport(id, items));
    }

    @Operation(summary = "AI生成问答对（异步任务，需要DEVELOPER及以上权限）")
    @PostMapping("/{id}/qa-pairs/ai-generate")
    public Result<Task> generateQaPairs(@PathVariable Long id,
                                         @RequestParam(defaultValue = "10") Integer count,
                                         @RequestParam(required = false) Long providerId,
                                         @RequestParam(required = false) String modelId) {
        return Result.ok(qaPairService.generateByAI(id, count, providerId, modelId));
    }

    @Operation(summary = "手动触发问答对向量化（需要DEVELOPER及以上权限）")
    @PostMapping("/qa-pairs/{qaPairId}/vectorize")
    public Result<Void> vectorizeQaPair(@PathVariable Long qaPairId) {
        qaPairService.vectorize(qaPairId);
        return Result.ok();
    }

    @Operation(summary = "批量触发问答对向量化（需要DEVELOPER及以上权限）")
    @PostMapping("/qa-pairs/batch-vectorize")
    public Result<Integer> batchVectorizeQaPairs(@RequestBody List<Long> qaPairIds) {
        return Result.ok(qaPairService.batchVectorize(qaPairIds));
    }
}

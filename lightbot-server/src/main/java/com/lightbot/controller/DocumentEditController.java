package com.lightbot.controller;

import com.lightbot.common.Result;
import com.lightbot.dto.DocumentEditRequest;
import com.lightbot.dto.DocumentEditSaveVO;
import com.lightbot.dto.DocumentVersionVO;
import com.lightbot.dto.EditableContentVO;
import com.lightbot.service.DocumentEditService;
import com.lightbot.service.DocumentVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 文档在线编辑接口
 *
 * @author finch
 * @since 2026-06-16
 */
@Tag(name = "文档在线编辑", description = "文档内容在线编辑、保存、重建")
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentEditController {

    private final DocumentEditService documentEditService;
    private final DocumentVersionService documentVersionService;

    @Operation(summary = "获取文档可编辑内容")
    @GetMapping("/{documentId}/editable-content")
    public Result<EditableContentVO> getEditableContent(@PathVariable Long documentId) {
        return Result.ok(documentEditService.getEditableContent(documentId));
    }

    @Operation(summary = "保存文档编辑内容（触发全量重建）")
    @PutMapping("/{documentId}/content")
    public Result<DocumentEditSaveVO> saveContent(@PathVariable Long documentId,
                                                   @RequestBody @jakarta.validation.Valid DocumentEditRequest request) {
        return Result.ok(documentEditService.saveContent(documentId, request));
    }

    @Operation(summary = "获取文档版本列表")
    @GetMapping("/{documentId}/versions")
    public Result<java.util.List<DocumentVersionVO>> listVersions(@PathVariable Long documentId) {
        return Result.ok(documentVersionService.listVersions(documentId));
    }

    @Operation(summary = "获取指定版本内容")
    @GetMapping("/{documentId}/versions/{versionId}")
    public Result<String> getVersionContent(@PathVariable Long documentId,
                                             @PathVariable Long versionId) {
        return Result.ok(documentVersionService.getVersionContent(versionId));
    }

    @Operation(summary = "回滚到指定版本")
    @PostMapping("/{documentId}/versions/{versionId}/rollback")
    public Result<Void> rollback(@PathVariable Long documentId,
                                  @PathVariable Long versionId) {
        documentVersionService.rollback(documentId, versionId);
        return Result.ok();
    }
}

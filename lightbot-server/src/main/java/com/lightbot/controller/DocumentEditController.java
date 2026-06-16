package com.lightbot.controller;

import com.lightbot.common.Result;
import com.lightbot.dto.DocumentEditRequest;
import com.lightbot.dto.DocumentEditSaveVO;
import com.lightbot.dto.EditableContentVO;
import com.lightbot.service.DocumentEditService;
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
}

package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档可编辑内容响应
 *
 * @author finch
 * @since 2026-06-16
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文档可编辑内容")
public class EditableContentVO {

    @Schema(description = "文档ID")
    private Long documentId;

    @Schema(description = "文件名称")
    private String fileName;

    @Schema(description = "文件类型")
    private String fileType;

    @Schema(description = "编辑器模式：editor/richtext/spreadsheet/unsupported")
    private String editMode;

    @Schema(description = "可编辑内容")
    private String content;

    @Schema(description = "文件哈希（乐观锁）")
    private String fileHash;

    @Schema(description = "是否可编辑")
    private Boolean editable;

    @Schema(description = "总字符数")
    private Integer totalChars;

    @Schema(description = "当前分块数")
    private Integer totalChunks;
}

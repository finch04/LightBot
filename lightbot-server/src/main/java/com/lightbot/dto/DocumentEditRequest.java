package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 文档编辑保存请求
 *
 * @author finch
 * @since 2026-06-16
 */
@Data
@Schema(description = "文档编辑保存请求")
public class DocumentEditRequest {

    @NotBlank(message = "编辑内容不能为空")
    @Schema(description = "编辑后的完整内容")
    private String content;

    @NotBlank(message = "编辑模式不能为空")
    @Schema(description = "编辑器模式：editor/richtext/spreadsheet")
    private String editMode;

    @Schema(description = "期望的文件哈希（乐观锁），为空时不校验")
    private String expectedHash;
}

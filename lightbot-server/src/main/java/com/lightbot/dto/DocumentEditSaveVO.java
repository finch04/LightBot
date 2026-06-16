package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档编辑保存结果
 *
 * @author finch
 * @since 2026-06-16
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文档编辑保存结果")
public class DocumentEditSaveVO {

    @Schema(description = "文档ID")
    private Long documentId;

    @Schema(description = "新的文件哈希")
    private String newHash;

    @Schema(description = "提示消息")
    private String message;
}

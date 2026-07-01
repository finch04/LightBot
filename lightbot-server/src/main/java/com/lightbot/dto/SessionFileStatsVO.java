package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 会话文件统计。
 *
 * @author finch
 * @since 2026-06-30
 */
@Data
@Schema(description = "会话文件统计")
public class SessionFileStatsVO {

    @Schema(description = "文件总数")
    private int total;

    @Schema(description = "用户上传文件数")
    private int userUpload;

    @Schema(description = "AI 生成文件数")
    private int aiGenerated;
}

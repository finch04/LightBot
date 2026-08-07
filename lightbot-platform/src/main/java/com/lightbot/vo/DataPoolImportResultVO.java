package com.lightbot.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 数据池同步导入结果
 *
 * @author finch
 * @since 2026-07-26
 */
@Data
@Schema(description = "数据池导入结果")
public class DataPoolImportResultVO {

    @Schema(description = "总条数")
    private Integer total;

    @Schema(description = "成功条数")
    private Integer successCount;

    @Schema(description = "失败条数")
    private Integer failCount;

    @Schema(description = "结果 Excel 文件名")
    private String resultFileName;

    @Schema(description = "结果 Excel（Base64，xlsx）")
    private String resultFileBase64;
}

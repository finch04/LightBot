package com.lightbot.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Prompt版本详情 VO
 *
 * @author finch
 * @since 2026-05-27
 */
@Data
@Schema(description = "Prompt版本详情")
public class PromptVersionVO {

    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "Prompt Key")
    private String promptKey;

    @Schema(description = "版本号")
    private String version;

    @Schema(description = "版本描述")
    private String versionDesc;

    @Schema(description = "模板内容")
    private String template;

    @Schema(description = "变量定义(JSON)")
    private String variables;

    @Schema(description = "模型配置(JSON)")
    private String modelConfig;

    @Schema(description = "版本状态")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}

package com.lightbot.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 企业 API Key 长期记忆策略（含生效结果）
 *
 * @author finch
 * @since 2026-08-01
 */
@Data
@Schema(description = "API Key 长期记忆策略")
public class LongMemoryKeyConfigVO {

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "API Key ID")
    private Long apiKeyId;

    @Schema(description = "是否跟随企业默认")
    private Boolean inherit;

    @Schema(description = "覆盖：是否启用")
    private Boolean enabled;

    @Schema(description = "覆盖：是否自动抽取")
    private Boolean autoExtract;

    @Schema(description = "覆盖：注入条数")
    private Integer injectLimit;

    @Schema(description = "覆盖：作用域")
    private String scope;

    @Schema(description = "解析后的生效策略")
    private LongMemoryPolicyVO effective;
}

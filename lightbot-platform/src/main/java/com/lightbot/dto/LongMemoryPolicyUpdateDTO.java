package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 企业长期记忆默认策略更新
 *
 * @author finch
 * @since 2026-08-01
 */
@Data
@Schema(description = "企业长期记忆默认策略更新")
public class LongMemoryPolicyUpdateDTO {

    @Schema(description = "是否启用长期记忆")
    private Boolean enabled;

    @Schema(description = "是否自动抽取记忆")
    private Boolean autoExtract;

    @Min(value = 1, message = "注入条数至少为 1")
    @Max(value = 15, message = "注入条数最多为 15")
    @Schema(description = "每轮注入记忆条数（1-15）")
    private Integer injectLimit;

    @Schema(description = "作用域：user=跨 Agent，agent=按 Agent")
    private String scope;
}

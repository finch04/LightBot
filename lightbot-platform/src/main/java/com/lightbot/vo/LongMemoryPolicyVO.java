package com.lightbot.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 长期记忆策略（企业默认或解析后的生效策略）
 *
 * @author finch
 * @since 2026-08-01
 */
@Data
@Schema(description = "长期记忆策略")
public class LongMemoryPolicyVO {

    @Schema(description = "是否启用长期记忆")
    private Boolean enabled;

    @Schema(description = "是否自动抽取记忆")
    private Boolean autoExtract;

    @Schema(description = "每轮注入记忆条数（1-15）")
    private Integer injectLimit;

    @Schema(description = "作用域：user=跨 Agent，agent=按 Agent")
    private String scope;
}

package com.lightbot.dto;

import com.lightbot.enums.MentionResourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Mention 候选资源项
 *
 * @author finch
 * @since 2026-06-29
 */
@Data
@Builder
@Schema(description = "Mention 候选资源项")
public class MentionResourceDTO {

    @Schema(description = "资源类型")
    private MentionResourceType type;

    @Schema(description = "资源ID（字符串形式）")
    private String resourceId;

    @Schema(description = "资源展示名")
    private String name;

    @Schema(description = "资源描述")
    private String description;

    @Schema(description = "原始 token")
    private String token;

    @Schema(description = "是否可用（资源已删除/禁用/无权限时为 false）")
    private boolean enabled;

    @Schema(description = "不可用原因（enabled=false 时填写）")
    private String disabledReason;

    @Schema(description = "扩展元信息")
    private Map<String, Object> meta;
}

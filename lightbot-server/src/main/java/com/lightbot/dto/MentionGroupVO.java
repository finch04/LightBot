package com.lightbot.dto;

import com.lightbot.enums.MentionResourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Mention 候选分组
 *
 * @author finch
 * @since 2026-06-29
 */
@Data
@Builder
@Schema(description = "Mention 候选分组")
public class MentionGroupVO {

    @Schema(description = "资源类型")
    private MentionResourceType type;

    @Schema(description = "分组展示标签")
    private String label;

    @Schema(description = "候选资源列表")
    private List<MentionResourceDTO> items;
}
